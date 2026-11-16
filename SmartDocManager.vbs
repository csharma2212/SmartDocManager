' SmartDocManager Silent Portable Launcher
Set fso = CreateObject("Scripting.FileSystemObject")
Set wshShell = CreateObject("WScript.Shell")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)

exePath = scriptDir & "\output\windows\SmartDocManager.exe"
If Not fso.FileExists(exePath) Then
    exePath = scriptDir & "\output\windows\portable\SmartDocManager.exe"
End If
If fso.FileExists(exePath) Then
    wshShell.CurrentDirectory = fso.GetParentFolderName(exePath)
    wshShell.Run """" & exePath & """", 0, False
Else
    pywPath = scriptDir & "\windows\.venv\Scripts\pythonw.exe"
    runScript = scriptDir & "\windows\run_app.py"
    If fso.FileExists(pywPath) And fso.FileExists(runScript) Then
        wshShell.CurrentDirectory = scriptDir & "\windows"
        wshShell.Run """" & pywPath & """ """ & runScript & """", 0, False
    Else
        MsgBox "SmartDocManager is not built yet. Please run build first.", vbExclamation, "SmartDocManager"
    End If
End If
