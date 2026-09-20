using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using SPORTident;
using SPORTident.Communication;
using SPORTident.Communication.UsbDevice;

// Optional engineering command. The packaged app does not invoke this writer.
static class CardWriteProbe
{
    public static int Run(string[] args, Action configureLicense)
    {
        Communication? communication = null;
        CardSession? session = null;
        var stage = "write request validation";
        var exitCode = 1;
        var writeAttempted = false;
        try
        {
            var request = ReadRequest(args);
            stage = "license configuration";
            configureLicense();
            Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
            stage = "SDK initialization";
            communication = CreateCommunication(args[0]);
            session = new CardSession(communication, request.CardNumber);
            stage = "station information read";
            ReadExpectedStation(communication, request.StationNumber);

            stage = "fresh SI-Card8 read before write";
            var initialRead = session.Arm();
            ReportPhase("WaitingForCard");
            Console.Error.WriteLine($"Station {request.StationNumber} verified. Remove and insert SI-Card8 {request.CardNumber}; keep it seated (45-second timeout).");
            var original = initialRead.WaitAsync(TimeSpan.FromSeconds(45)).GetAwaiter().GetResult();
            if ((original.PersonalData.FirstName ?? "") != request.ExpectedFirstName ||
                (original.PersonalData.LastName ?? "") != request.ExpectedLastName)
                throw new InvalidDataException();
            if (request.FirstName == request.ExpectedFirstName && request.LastName == request.ExpectedLastName)
                throw new InvalidDataException(); // A no-op cannot demonstrate programming.

            var punchesBefore = CapturePunches(original);
            var punchCountBefore = original.ControlPunchList.Count;
            var feedbackBefore = Hex(original.FeedbackSel);
            var characterSetBefore = original.CharacterSet;
            var personalData = new CardPersonalData(original.PersonalData)
            {
                FirstName = request.FirstName,
                LastName = request.LastName,
            };
            stage = "vendor name validation";
            if (CardPersonalData.ValidatePersonalData(CardType.Card8, personalData, SiCardCharacterSet.Default,
                    out _, out _) != PersonalDataValidationResult.Valid)
                throw new InvalidDataException();
            var settings = new CardConfigSettings(personalData, SiCardCharacterSet.Default, FeedbackSignal.NotSet)
            {
                AutoApplyOnCardIn = false,
            };

            stage = "SI-Card8 name write";
            ReportPhase("Writing");
            Console.Error.WriteLine($"Writing first name '{request.FirstName}', last name '{request.LastName}' to card {request.CardNumber}; possible punch loss was accepted in the request.");
            // There is one SDK write invocation. Removal/failure permanently invalidates this session.
            writeAttempted = true;
            session.Write(settings).WaitAsync(TimeSpan.FromSeconds(10)).GetAwaiter().GetResult();
            stage = "reopen for independent read-back";
            session.Disarm();
            communication.Close();
            Console.Error.WriteLine("SDK write completed; initial serial connection closed for independent verification.");
            communication = CreateCommunication(args[0]);
            session = new CardSession(communication, request.CardNumber);
            ReadExpectedStation(communication, request.StationNumber);
            stage = "SI-Card8 read-back verification";
            var verificationRead = session.Arm();
            ReportPhase("WaitingForReadBack");
            Console.Error.WriteLine($"Read-back ready. Remove and reinsert SI-Card8 {request.CardNumber}; keep it seated (45-second timeout). No further write will occur.");
            var readBack = verificationRead.WaitAsync(TimeSpan.FromSeconds(45)).GetAwaiter().GetResult();
            if ((readBack.PersonalData.FirstName ?? "") != request.FirstName ||
                (readBack.PersonalData.LastName ?? "") != request.LastName)
                throw new InvalidDataException();
            var punchesPreserved = punchesBefore == CapturePunches(readBack);
            var feedbackPreserved = feedbackBefore == Hex(readBack.FeedbackSel);
            var characterSetPreserved = characterSetBefore == readBack.CharacterSet;
            Console.WriteLine(JsonSerializer.Serialize(new WriteResult(1, request.StationNumber, readBack.SiidValue,
                "SI-Card8", readBack.PersonalData.FirstName ?? "", readBack.PersonalData.LastName ?? "",
                punchCountBefore, readBack.ControlPunchList.Count,
                punchesPreserved, feedbackPreserved, characterSetPreserved)));
            if (!punchesPreserved || !feedbackPreserved || !characterSetPreserved)
                throw new InvalidDataException();
            exitCode = 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"Write probe failed during {stage}: {exception.GetType().Name}.");
            if (writeAttempted)
                Console.Error.WriteLine("A write may have occurred. Do not retry automatically; independently read the card before proceeding.");
        }
        finally
        {
            session?.Disarm();
            if (communication?.IsOpen == true)
            {
                try
                {
                    communication.Close();
                    Console.Error.WriteLine("Serial connection closed.");
                }
                catch (Exception exception)
                {
                    Console.Error.WriteLine($"Serial close failed: {exception.GetType().Name}.");
                    exitCode = 1;
                }
            }
        }
        return exitCode;
    }

    static void ReportPhase(string phase) => Console.WriteLine(JsonSerializer.Serialize(new
    {
        SchemaVersion = 1, Event = "Phase", Phase = phase,
    }));

    static Communication CreateCommunication(string port) => new()
    {
        DeviceConnection = new DeviceInfo(DeviceType.Serial, port),
        TargetDevice = TargetDevice.Direct,
        CardsReadMode = CardsReadMode.DetectOnly,
        BaudRate = 38400,
        SiacReadonlyMode = true,
        SiacMeasureBatteryOnRead = false,
    };

    static void ReadExpectedStation(Communication sdk, uint expectedStation)
    {
        var result = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        sdk.StationConfigRead += (_, e) => result.TrySetResult(e.Device.SerialNumber);
        sdk.CommunicationFailed += (_, _) => result.TrySetException(new IOException());
        sdk.Open();
        sdk.GetSystemData();
        if (result.Task.WaitAsync(TimeSpan.FromSeconds(10)).GetAwaiter().GetResult() !=
            expectedStation.ToString(CultureInfo.InvariantCulture)) throw new InvalidDataException();
    }

    static WriteRequest ReadRequest(string[] args)
    {
        if (string.IsNullOrWhiteSpace(args[0]) ||
            !uint.TryParse(args[1], NumberStyles.None, CultureInfo.InvariantCulture, out var station) || station == 0 ||
            !long.TryParse(args[2], NumberStyles.None, CultureInfo.InvariantCulture, out var card) || card <= 0 ||
            new FileInfo(args[4]).Length > 4096) throw new InvalidDataException();
        var request = JsonSerializer.Deserialize<WriteRequest>(File.ReadAllText(args[4]), new JsonSerializerOptions
        {
            UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
        }) ?? throw new InvalidDataException();
        if (request.SchemaVersion != 1 || request.StationNumber != station || request.CardNumber != card ||
            !request.AcceptPossiblePunchLoss || request.ExpectedFirstName == null || request.ExpectedLastName == null ||
            !ValidName(request.FirstName) || !ValidName(request.LastName) ||
            request.FirstName.Length + request.LastName.Length > 23) throw new InvalidDataException();
        return request;
    }

    static bool ValidName(string? name) => name != null && name == name.Trim(' ') &&
        name.All(character => character != ';' && RoundTripsDefaultCharacterSet(character));

    static bool RoundTripsDefaultCharacterSet(char character)
    {
        if (character is >= ' ' and <= '~') return true;
        if (character is < '\u00a0' or > '\u00ff') return false;
        var stored = CardPersonalData.ReplacePrinterCharsetBytes(
            new[] { (byte)character }, SiCardCharacterSet.Default);
        if (stored.Length != 1 || stored[0] == 0 || stored[0] == 0xEE) return false;
        var decoded = CardPersonalData.ReplacePrinterCharsetBytes(
            stored, SiCardCharacterSet.Default, fromPrinter: true);
        return decoded.Length == 1 && decoded[0] == (byte)character;
    }

    // Capture immutable values immediately: a later SDK event may reuse mutable card objects.
    static string CapturePunches(SportidentCard card) => JsonSerializer.Serialize(new
    {
        card.ClearCounter,
        card.ControlPunchPointer,
        Clear = Punch(card.ClearPunch), Check = Punch(card.CheckPunch), Start = Punch(card.StartPunch),
        Finish = Punch(card.FinishPunch), ClearReserve = Punch(card.ClearPunchReserve),
        StartReserve = Punch(card.StartPunchReserve), FinishReserve = Punch(card.FinishPunchReserve),
        Controls = card.ControlPunchList.Select(Punch).ToArray(),
    });

    static object? Punch(CardPunchData? punch) => punch == null ? null : new
    {
        punch.CodeNumber, punch.OperatingMode, punch.DayOfWeek, punch.IsMissingOrEmpty,
        Time = punch.TimeSI?.Value, Raw = Hex(punch.RawValue),
    };

    static string? Hex(byte[]? bytes) => bytes == null ? null : Convert.ToHexString(bytes);

    sealed record WriteRequest(int SchemaVersion, uint StationNumber, long CardNumber,
        string ExpectedFirstName, string ExpectedLastName, string FirstName, string LastName, bool AcceptPossiblePunchLoss);
    sealed record WriteResult(int SchemaVersion, uint StationNumber, long CardNumber, string CardType,
        string FirstName, string LastName, int ControlPunchCountBefore, int ControlPunchCountAfter,
        bool PunchesPreserved, bool FeedbackPreserved, bool CharacterSetPreserved);

    sealed class CardSession
    {
        readonly Communication sdk;
        readonly long expectedCard;
        readonly object gate = new();
        TaskCompletionSource<SportidentCard>? reading;
        readonly TaskCompletionSource<bool> written = new(TaskCreationOptions.RunContinuationsAsynchronously);
        bool armed, detected, writeStarted, invalid;

        public CardSession(Communication sdk, long expectedCard)
        {
            this.sdk = sdk;
            this.expectedCard = expectedCard;
            sdk.CommunicationFailed += (_, _) => { lock (gate) Invalidate(); };
            sdk.SiCardOut += (_, _) => { lock (gate) { if (detected) Invalidate(); } };
            sdk.SiCardIn += (_, e) =>
            {
                lock (gate)
                {
                    if (!armed) return;
                    if (detected || !Matches(e.Card)) { Invalidate(); return; }
                    detected = true;
                    try { sdk.ReadCurrentSiCard(CardsReadMode.ReadCards); }
                    catch (Exception) { Invalidate(); }
                }
            };
            sdk.SiCardReadCompleted += (_, e) =>
            {
                lock (gate)
                {
                    if (!armed || invalid || reading == null) return;
                    if (e.Cards.Length != 1 || !Matches(e.Cards[0]) || e.Cards[0].PersonalData == null ||
                        e.Cards[0].ControlPunchList == null) { Invalidate(); return; }
                    reading.TrySetResult(e.Cards[0]);
                    reading = null;
                }
            };
            sdk.SiCardWriteCompleted += (_, e) =>
            {
                lock (gate)
                {
                    if (!armed || invalid || !writeStarted) return;
                    if (!Matches(e.Card)) { Invalidate(); return; }
                    written.TrySetResult(true);
                }
            };
        }

        public Task<SportidentCard> Arm()
        {
            lock (gate)
            {
                if (invalid || armed) throw new InvalidDataException();
                armed = true;
                reading = NewRead();
                return reading.Task;
            }
        }

        public Task<bool> Write(CardConfigSettings settings)
        {
            lock (gate)
            {
                RequirePresent();
                if (writeStarted || reading != null) throw new InvalidDataException();
                writeStarted = true;
                sdk.SetSiCardPersonalDataAndFeedback(settings);
                return written.Task;
            }
        }

        public void Disarm() { lock (gate) armed = false; }
        void RequirePresent()
        {
            if (!armed || !detected || invalid || !sdk.IsSiCardPresent ||
                sdk.CurrentSiid != expectedCard.ToString(CultureInfo.InvariantCulture)) throw new InvalidDataException();
        }
        bool Matches(SportidentCard card) => card.SiidValue == expectedCard && card.CardType == CardType.Card8;
        void Invalidate()
        {
            invalid = true;
            reading?.TrySetException(new InvalidDataException());
            written.TrySetException(new InvalidDataException());
        }
        static TaskCompletionSource<SportidentCard> NewRead() => new(TaskCreationOptions.RunContinuationsAsynchronously);
    }
}
