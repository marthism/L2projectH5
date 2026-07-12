# =====================================================================
# Auto Login - Lineage 2 HighFive
# Salva contas em AutoLoginAccounts.txt (ao lado deste script) e digita
# usuario/senha automaticamente na tela de login do client.
#
# Formato do AutoLoginAccounts.txt (uma conta por linha):
#   NomeAmigavel|usuario|senha
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File AutoLogin.ps1            (menu)
#   powershell -ExecutionPolicy Bypass -File AutoLogin.ps1 NomeConta  (direto)
#
# Observacao: o client precisa estar com a tela de login em foco quando
# a digitacao comecar. Ajuste $LoadDelaySeconds se o seu PC demorar mais
# para abrir o jogo.
# =====================================================================

param([string]$AccountName)

# ========================== CONFIG ===================================
$ClientExe = 'C:\L2Game\Client\system\L2.exe'
$LoadDelaySeconds = 25                     # tempo de espera ate a tela de login
# =====================================================================

$ErrorActionPreference = 'Stop'
$accountsFile = Join-Path $PSScriptRoot 'AutoLoginAccounts.txt'

if (-not (Test-Path $accountsFile)) {
    @(
        '# Uma conta por linha: NomeAmigavel|usuario|senha'
        '# Exemplo:'
        '# Principal|admin|minhasenha'
    ) | Set-Content -Path $accountsFile -Encoding utf8
    Write-Host "Arquivo criado: $accountsFile"
    Write-Host 'Adicione suas contas nele e rode o script novamente.'
    exit 0
}

$accounts = @(Get-Content $accountsFile | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
    $parts = $_.Split('|')
    if ($parts.Count -ge 3) {
        [pscustomobject]@{ Name = $parts[0].Trim(); User = $parts[1].Trim(); Pass = $parts[2].Trim() }
    }
})

if ($accounts.Count -eq 0) {
    Write-Host "Nenhuma conta cadastrada em $accountsFile"
    exit 1
}

$selected = $null
if ($AccountName) {
    $selected = $accounts | Where-Object { $_.Name -ieq $AccountName } | Select-Object -First 1
    if ($null -eq $selected) {
        Write-Host "Conta '$AccountName' nao encontrada."
        exit 1
    }
} else {
    Write-Host '===== Contas salvas ====='
    for ($i = 0; $i -lt $accounts.Count; $i++) {
        Write-Host ("  {0}) {1}  (usuario: {2})" -f ($i + 1), $accounts[$i].Name, $accounts[$i].User)
    }
    $choice = Read-Host 'Escolha o numero da conta'
    $index = 0
    if (-not [int]::TryParse($choice, [ref]$index) -or ($index -lt 1) -or ($index -gt $accounts.Count)) {
        Write-Host 'Opcao invalida.'
        exit 1
    }
    $selected = $accounts[$index - 1]
}

if (-not (Test-Path $ClientExe)) {
    Write-Host "Client nao encontrado em: $ClientExe"
    Write-Host 'Edite a variavel $ClientExe no topo deste script.'
    exit 1
}

# Caracteres especiais do SendKeys precisam ser escapados.
function Protect-SendKeys([string]$value) {
    $result = [Text.StringBuilder]::new()
    foreach ($ch in $value.ToCharArray()) {
        if ('+^%~(){}[]'.Contains($ch)) {
            [void]$result.Append('{').Append($ch).Append('}')
        } else {
            [void]$result.Append($ch)
        }
    }
    return $result.ToString()
}

Write-Host ("Abrindo o client para a conta '{0}'..." -f $selected.Name)
$process = Start-Process -FilePath $ClientExe -WorkingDirectory (Split-Path $ClientExe) -PassThru

Write-Host ("Aguardando {0}s a tela de login..." -f $LoadDelaySeconds)
Start-Sleep -Seconds $LoadDelaySeconds

Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Win32Focus {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
}
'@

# Traz o client para o foco antes de digitar.
$process.Refresh()
if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
    [void][Win32Focus]::SetForegroundWindow($process.MainWindowHandle)
    Start-Sleep -Milliseconds 700
}

[System.Windows.Forms.SendKeys]::SendWait((Protect-SendKeys $selected.User))
Start-Sleep -Milliseconds 400
[System.Windows.Forms.SendKeys]::SendWait('{TAB}')
Start-Sleep -Milliseconds 300
[System.Windows.Forms.SendKeys]::SendWait((Protect-SendKeys $selected.Pass))
Start-Sleep -Milliseconds 400
[System.Windows.Forms.SendKeys]::SendWait('{ENTER}')

Write-Host 'Credenciais enviadas. Bom jogo!'
