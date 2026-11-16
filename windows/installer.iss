; Inno Setup Script for SmartDocManager Windows Installer Wizard
#define MyAppName "SmartDocManager"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "CSharma"
#define MyAppExeName "SmartDocManager.exe"

[Setup]
AppId={{974844DF-26D9-4C20-869D-AC31187BD41A}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/SmartDocManager
AppSupportURL=https://github.com/SmartDocManager
AppUpdatesURL=https://github.com/SmartDocManager
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequiredOverridesAllowed=dialog commandline
PrivilegesRequired=lowest
OutputDir=..\output\windows\installer
OutputBaseFilename=SmartDocManager_Setup
SetupIconFile=assets\icon.ico
Compression=lzma2/normal
SolidCompression=no
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
CloseApplications=yes
RestartApplications=no
VersionInfoVersion=1.0.0.0
VersionInfoCompany=CSharma
VersionInfoDescription=SmartDocManager Setup
VersionInfoCopyright=Copyright (C) 2026 CSharma
VersionInfoProductName=SmartDocManager
VersionInfoProductVersion=1.0.0.0

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\output\windows\dist\SmartDocManager\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
