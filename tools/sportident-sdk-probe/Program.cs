using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using SPORTident.Communication;
using SPORTident.Communication.Licensing.Common;
using SPORTident.Communication.UsbDevice;

return Run(args);

static int Run(string[] args)
{
    if (args.Length != 2 || string.IsNullOrWhiteSpace(args[0]) ||
        !uint.TryParse(args[1], NumberStyles.None, CultureInfo.InvariantCulture, out var expectedStation) ||
        expectedStation == 0)
    {
        Console.Error.WriteLine("Usage: sportident-sdk-probe <serial-port> <expected-station-number>");
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
        communication.StationConfigRead += (_, e) => stationRead.TrySetResult(new StationRead(
            e.Device.SerialNumber.ToString(CultureInfo.InvariantCulture), e.Device.Product.ProductString));
        communication.CommunicationFailed += (_, _) => stationRead.TrySetException(new IOException());

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
            Console.WriteLine($"Station read verified: {station.Number} [{station.Product}].");
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
                Console.WriteLine("Serial connection closed.");
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
