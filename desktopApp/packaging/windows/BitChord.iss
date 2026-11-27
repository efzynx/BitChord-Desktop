; Inno Setup script for BitChord Desktop Windows Installer
; Supports custom installation paths, Info/Privacy/Terms acceptance wizard,
; and seamless in-place upgrades over previous Inno Setup or jpackage MSI/EXE installs.

#ifndef AppVersion
  #define AppVersion "1.10.0"
#endif
#ifndef NumericVersion
  #define NumericVersion "1.10.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\..\build\compose\binaries\main\app\BitChord"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\build\distributions"
#endif
#ifndef IconFile
  #define IconFile "..\icons\AppIcon.ico"
#endif

[Setup]
AppId={{8F3C1D24-6A2B-4F5E-9C17-2B8D54E0A913}
AppName=BitChord Desktop
AppVersion={#AppVersion}
AppVerName=BitChord Desktop {#AppVersion}
AppPublisher=BitChord Desktop Community
AppPublisherURL=https://bitchord-desktop.efzyn.my.id
AppSupportURL=https://github.com/efzynx/BitChord-Desktop/issues
AppUpdatesURL=https://github.com/efzynx/BitChord-Desktop/releases
VersionInfoVersion={#NumericVersion}
VersionInfoProductName=BitChord Desktop
VersionInfoProductVersion={#NumericVersion}
VersionInfoTextVersion={#AppVersion}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\BitChord
UsePreviousAppDir=yes
DefaultGroupName=BitChord
AllowNoIcons=yes
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=yes
DisableReadyPage=no
InfoBeforeFile=about-install.txt
LicenseFile=terms-and-privacy.txt
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\BitChord.exe
UninstallDisplayName=BitChord Desktop
OutputDir={#OutputDir}
OutputBaseFilename=BitChord-{#AppVersion}
Compression=lzma2/normal
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=BitChord.exe,party-server.exe
RestartApplications=no
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; Remove previous bundled app jars and embedded JDK runtime cleanly during overwrite upgrades
; while keeping user data untouched in %LOCALAPPDATA%\BitChord and Registry Preferences.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\BitChord Desktop"; Filename: "{app}\BitChord.exe"
Name: "{group}\Uninstall BitChord Desktop"; Filename: "{uninstallexe}"
Name: "{autodesktop}\BitChord Desktop"; Filename: "{app}\BitChord.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\BitChord.exe"; Description: "{cm:LaunchProgram,BitChord Desktop}"; Flags: nowait postinstall skipifsilent

[Code]
procedure CloseRunningBitChord();
var
  ResultCode: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM BitChord.exe /T', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM party-server.exe /T', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(250);
end;

procedure UninstallLegacyMsiInRoot(const RootKey: Integer);
var
  UninstallPath: string;
  Subkeys: TArrayOfString;
  I: Integer;
  SubkeyPath: string;
  DisplayName: string;
  IsMsi: Cardinal;
  ResultCode: Integer;
begin
  UninstallPath := 'Software\Microsoft\Windows\CurrentVersion\Uninstall';
  if not RegGetSubkeyNames(RootKey, UninstallPath, Subkeys) then
    Exit;

  for I := 0 to GetArrayLength(Subkeys) - 1 do
  begin
    // Legacy jpackage MSI entries use a raw "{GUID}" subkey rather than Inno Setup's "{AppId}_is1".
    if (Length(Subkeys[I]) > 2) and (Subkeys[I][1] = '{') and (Pos('_is1', Subkeys[I]) = 0) then
    begin
      SubkeyPath := UninstallPath + '\' + Subkeys[I];
      if RegQueryStringValue(RootKey, SubkeyPath, 'DisplayName', DisplayName) then
      begin
        if (CompareText(DisplayName, 'BitChord') = 0) or (CompareText(DisplayName, 'BitChord Desktop') = 0) then
        begin
          IsMsi := 0;
          RegQueryDWordValue(RootKey, SubkeyPath, 'WindowsInstaller', IsMsi);
          if IsMsi = 1 then
          begin
            Exec(
              ExpandConstant('{sys}\msiexec.exe'),
              '/x ' + Subkeys[I] + ' /qn /norestart',
              '',
              SW_HIDE,
              ewWaitUntilTerminated,
              ResultCode
            );
          end;
        end;
      end;
    end;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
  begin
    CloseRunningBitChord();
    UninstallLegacyMsiInRoot(HKCU);
    if IsAdminInstallMode then
      UninstallLegacyMsiInRoot(HKLM);
  end;
end;
