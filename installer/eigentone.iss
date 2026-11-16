; Eigentone's installer, built with Inno Setup 6:
;   iscc installer\eigentone.iss            (from the repository root, after building the engine and the app)
;
; It installs into Program Files\Eigentone:
;   App\      the app, with its own Java runtime and the agent that lists the apps playing
;   Setup\    the plug-in, the background service and the tool that hooks them into Windows audio
; then runs that tool, which registers the plug-in, puts it first in the default output's effects, copies the
; plug-in and service beside it, and starts the service. Uninstalling runs the tool's uninstall first, which puts
; every output's effects back exactly as they were, before any file is removed.

#ifndef Version
  #define Version "0.1.0"
#endif
#define Root ".."

[Setup]
AppId={{6C7C0C43-5C55-4F8E-9D8A-2A4F0C1B7E11}
AppName=Eigentone
AppVersion={#Version}
AppVerName=Eigentone {#Version}
AppPublisher=Dragjon
AppPublisherURL=https://github.com/Dragjon/eigentone-windows
AppSupportURL=https://github.com/Dragjon/eigentone-windows/issues
DefaultDirName={autopf}\Eigentone
DisableDirPage=yes
DisableProgramGroupPage=yes
DefaultGroupName=Eigentone
LicenseFile={#Root}\LICENSE
OutputDir={#Root}\build\installer
OutputBaseFilename=Eigentone-{#Version}-setup
SetupIconFile={#Root}\app\icons\eigentone.ico
UninstallDisplayIcon={app}\App\Eigentone.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; Registering an audio plug-in and a service needs an administrator, once.
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; Windows 11: the list of endpoint effects the plug-in joins arrived with it.
MinVersion=10.0.22000
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Add a shortcut to the desktop"; Flags: unchecked

[Files]
Source: "{#Root}\app\build\compose\binaries\main\app\Eigentone\*"; DestDir: "{app}\App"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Root}\build\bin\EigentoneApo.dll"; DestDir: "{app}\Setup"; Flags: ignoreversion
Source: "{#Root}\build\bin\eigentone-service.exe"; DestDir: "{app}\Setup"; Flags: ignoreversion
Source: "{#Root}\build\bin\eigentone-setup.exe"; DestDir: "{app}\Setup"; Flags: ignoreversion
Source: "{#Root}\THIRD_PARTY_NOTICES.txt"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Eigentone"; Filename: "{app}\App\Eigentone.exe"
Name: "{autodesktop}\Eigentone"; Filename: "{app}\App\Eigentone.exe"; Tasks: desktopicon

[Registry]
; The app adds itself to the user's sign-in apps; it goes with the app.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "Eigentone"; Flags: uninsdeletevalue dontcreatekey

[Run]
Filename: "{app}\App\Eigentone.exe"; Description: "Open Eigentone"; Flags: nowait postinstall skipifsilent runasoriginaluser

[Code]
// Eigentone and its agent hold their files open, so they're closed first, whether installing over an older
// copy or uninstalling.
procedure CloseEigentone();
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/f /im Eigentone.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/f /im eigentone-agent.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  CloseEigentone();
  Result := '';
end;

// After the files are copied: hook the plug-in into Windows audio and start the service. Windows audio pauses
// for a moment while it restarts.
procedure CurStepChanged(CurStep: TSetupStep);
var
  Code: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    WizardForm.StatusLabel.Caption := 'Adding Eigentone to Windows audio. Sound pauses for a moment...';
    if not Exec(ExpandConstant('{app}\Setup\eigentone-setup.exe'), 'install', ExpandConstant('{app}\Setup'),
                SW_HIDE, ewWaitUntilTerminated, Code) or (Code <> 0) then
      MsgBox('Eigentone was copied, but adding it to Windows audio didn''t finish (code ' + IntToStr(Code) + '). ' +
             'Restart the PC and run this installer again.', mbError, MB_OK);
  end;
end;

// Before any file is removed: put every output's effects back as they were and remove the service.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Code: Integer;
begin
  if CurUninstallStep = usUninstall then
  begin
    CloseEigentone();
    Exec(ExpandConstant('{app}\Setup\eigentone-setup.exe'), 'uninstall', ExpandConstant('{app}\Setup'),
         SW_HIDE, ewWaitUntilTerminated, Code);
  end;
end;
