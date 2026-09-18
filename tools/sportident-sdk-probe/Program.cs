using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using SPORTident;
using SPORTident.Communication;
using SPORTident.Communication.Licensing.Common;
using SPORTident.Communication.UsbDevice;

return Run(args);

static int Run(string[] args)
{
    long expectedCard = 0;
    if ((args.Length != 2 && args.Length != 3) || string.IsNullOrWhiteSpace(args[0]) ||
        !uint.TryParse(args[1], NumberStyles.None, CultureInfo.InvariantCulture, out var expectedStation) ||
        expectedStation == 0 || (args.Length == 3 &&
        (!long.TryParse(args[2], NumberStyles.None, CultureInfo.InvariantCulture, out expectedCard) || expectedCard <= 0)))
    {
        Console.Error.WriteLine("Usage: sportident-sdk-probe <serial-port> <expected-station-number> [expected-SI-Card8-number]");
        return 1;
    }

    Communication? communication = null;
    var stage = "license configuration";
    var exitCode = 1;
    try
    {
        ConfigureLicense();
        Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
        stage = "SDK initialization";
        communication = new Communication
        {
            DeviceConnection = new DeviceInfo(DeviceType.Serial, args[0]),
            TargetDevice = TargetDevice.Direct,
            CardsReadMode = CardsReadMode.DetectOnly,
            BaudRate = 38400,
            SiacReadonlyMode = true,
            SiacMeasureBatteryOnRead = false,
        };
        var stationRead = new TaskCompletionSource<StationRead>(TaskCreationOptions.RunContinuationsAsynchronously);
        var cardRead = new TaskCompletionSource<CardRead>(TaskCreationOptions.RunContinuationsAsynchronously);
        var cardReadArmed = 0;
        var cardReadStarted = 0;
        communication.StationConfigRead += (_, e) => stationRead.TrySetResult(new StationRead(
            e.Device.SerialNumber.ToString(CultureInfo.InvariantCulture), e.Device.Product.ProductString));
        communication.CommunicationFailed += (_, _) =>
        {
            stationRead.TrySetException(new IOException());
            cardRead.TrySetException(new IOException());
        };
        communication.SiCardIn += (_, e) =>
        {
            if (Volatile.Read(ref cardReadArmed) == 0 ||
                Interlocked.CompareExchange(ref cardReadStarted, 1, 0) != 0) return;
            if (e.Card.SiidValue != expectedCard || e.Card.CardType != CardType.Card8)
            {
                cardRead.TrySetException(new InvalidDataException());
                return;
            }
            try
            {
                communication.ReadCurrentSiCard(CardsReadMode.ReadCards);
            }
            catch (Exception)
            {
                cardRead.TrySetException(new IOException());
            }
        };
        communication.SiCardOut += (_, _) =>
        {
            if (Volatile.Read(ref cardReadStarted) != 0)
                cardRead.TrySetException(new InvalidDataException());
        };
        communication.SiCardReadCompleted += (_, e) =>
        {
            if (Volatile.Read(ref cardReadArmed) == 0 || Volatile.Read(ref cardReadStarted) == 0) return;
            if (e.Cards.Length != 1 || e.Cards[0].SiidValue != expectedCard ||
                e.Cards[0].CardType != CardType.Card8 || e.Cards[0].PersonalData == null ||
                e.Cards[0].ControlPunchList == null)
            {
                cardRead.TrySetException(new InvalidDataException());
                return;
            }
            var card = e.Cards[0];
            cardRead.TrySetResult(new CardRead(1, expectedStation, card.SiidValue, "SI-Card8",
                card.PersonalData.FirstName ?? "", card.PersonalData.LastName ?? "", card.ControlPunchList.Count));
        };

        stage = "serial open";
        communication.Open();
        stage = "station information read";
        communication.GetSystemData();
        var station = stationRead.Task.WaitAsync(TimeSpan.FromSeconds(10)).GetAwaiter().GetResult();
        if (station.Number != expectedStation.ToString(CultureInfo.InvariantCulture))
        {
            Console.Error.WriteLine($"Unexpected station {station.Number}; expected {expectedStation}.");
        }
        else
        {
            Console.Error.WriteLine($"Station read verified: {station.Number} [{station.Product}].");
            if (expectedCard != 0)
            {
                stage = "SI-Card8 owner information read";
                Volatile.Write(ref cardReadArmed, 1);
                Console.Error.WriteLine($"Remove and insert SI-Card8 {expectedCard}; keep it seated until the read finishes (45-second timeout).");
                var card = cardRead.Task.WaitAsync(TimeSpan.FromSeconds(45)).GetAwaiter().GetResult();
                Console.WriteLine(JsonSerializer.Serialize(card));
            }
            exitCode = 0;
        }
    }
    catch (Exception exception)
    {
        // Vendor exception text may contain license material. Emit only the stage and type.
        Console.Error.WriteLine($"Probe failed during {stage}: {exception.GetType().Name}.");
    }
    finally
    {
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

static void ConfigureLicense()
{
    var path = Environment.GetEnvironmentVariable("SPORTIDENT_SDK_LICENSE_FILE");
    if (string.IsNullOrWhiteSpace(path)) throw new InvalidOperationException();
    var text = File.ReadAllText(path);
    var type = Regex.Match(text, @"License\.Type\s*=\s*LicenseType\.(\w+)");
    if (!type.Success) throw new InvalidOperationException();
    License.Type = Enum.Parse<LicenseType>(type.Groups[1].Value);
    License.Name = ReadLicenseAssignment(text, "Name");
    License.Key = ReadLicenseAssignment(text, "Key");
}

static string ReadLicenseAssignment(string text, string field)
{
    var assignment = Regex.Match(text, @"License\." + field + "\\s*=\\s*\"([^\"]+)\"");
    if (!assignment.Success) throw new InvalidOperationException();
    return assignment.Groups[1].Value;
}

sealed record StationRead(string Number, string Product);
sealed record CardRead(int SchemaVersion, uint StationNumber, long CardNumber, string CardType,
    string FirstName, string LastName, int ControlPunchCount);
