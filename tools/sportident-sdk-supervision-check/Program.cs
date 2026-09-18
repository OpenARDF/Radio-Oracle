using System.Diagnostics;
using System.Reflection;

// BCL-only supervision checks: no SDK, license file, or serial transport.
if (args.FirstOrDefault() == "child")
{
    ParentPipeWatch.Start();
    Console.WriteLine("ready");
    Thread.Sleep(Timeout.Infinite);
    return;
}
if (args.FirstOrDefault() == "parent")
{
    using var child = Start("child");
    Require(child.StandardOutput.ReadLine() == "ready", "Child did not start.");
    Console.WriteLine(child.Id);
    Thread.Sleep(Timeout.Infinite);
    return;
}

using (var child = Start("child"))
{
    Require(child.StandardOutput.ReadLine() == "ready", "Child did not start.");
    Require(!child.WaitForExit(200), "Child exited while the parent pipe was open.");
    child.StandardInput.Close();
    Require(child.WaitForExit(5000) && child.ExitCode == ParentPipeWatch.ParentLostExitCode, "EOF did not stop the child.");
}
Console.WriteLine("PASS: open parent pipe keeps child alive; EOF stops it.");

using (var child = Start("child"))
{
    Require(child.StandardOutput.ReadLine() == "ready", "Child did not start.");
    child.StandardInput.Write('x');
    child.StandardInput.Flush();
    Require(child.WaitForExit(5000) && child.ExitCode == ParentPipeWatch.ParentLostExitCode, "Unexpected input did not stop the child.");
}
Console.WriteLine("PASS: unexpected pipe input stops the child.");

using (var parent = Start("parent"))
{
    var childId = int.Parse(parent.StandardOutput.ReadLine() ?? throw new InvalidOperationException("Missing child PID."));
    using var child = Process.GetProcessById(childId);
    try
    {
        parent.Kill(); // Deliberately bypass shutdown hooks on a test-only parent process.
        Require(parent.WaitForExit(5000), "Test parent did not exit.");
        Require(child.WaitForExit(5000), "Child survived loss of its parent.");
    }
    finally
    {
        if (!parent.HasExited) parent.Kill();
        if (!child.HasExited) child.Kill();
    }
}
Console.WriteLine("PASS: abrupt parent termination stops the child.");

static Process Start(string mode)
{
    var executable = Environment.ProcessPath ?? throw new InvalidOperationException("Missing executable path.");
    var info = new ProcessStartInfo(executable)
    {
        UseShellExecute = false, RedirectStandardInput = true, RedirectStandardOutput = true,
    };
    if (Path.GetFileNameWithoutExtension(executable).Equals("dotnet", StringComparison.OrdinalIgnoreCase))
        info.ArgumentList.Add(Assembly.GetExecutingAssembly().Location);
    info.ArgumentList.Add(mode);
    return Process.Start(info) ?? throw new InvalidOperationException("Could not start fixture.");
}

static void Require(bool condition, string message)
{
    if (!condition) throw new InvalidOperationException(message);
}
