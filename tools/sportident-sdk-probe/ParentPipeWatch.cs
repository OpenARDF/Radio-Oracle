/** Desktop owns the stdin pipe. Losing it must stop the helper, including after a parent crash. */
internal static class ParentPipeWatch
{
    public const int ParentLostExitCode = 15;

    public static void Start()
    {
        if (!Console.IsInputRedirected) throw new InvalidOperationException("A parent pipe is required.");
        var watcher = new Thread(() =>
        {
            try { Console.OpenStandardInput().ReadByte(); }
            catch (IOException) { }
            // No input is valid. EOF, data, or pipe failure stops the process and releases serial handles.
            Environment.Exit(ParentLostExitCode);
        }) { IsBackground = true, Name = "desktop-parent-pipe" };
        watcher.Start();
    }
}
