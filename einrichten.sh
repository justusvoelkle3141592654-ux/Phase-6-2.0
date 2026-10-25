#!/usr/bin/env bash
# WordFlow – Einrichtungsassistent
#
# Das Repository ist privat. Erst einen Zugangs-Token erstellen (nur Lesen, siehe
# ./einrichten.sh hilfe), dann:
#
#   git clone https://x-access-token:<TOKEN>@github.com/rcode125/Phase-6-2.0.git wordflow
#   cd wordflow && ./einrichten.sh
#
# Der Assistent speichert den Token in .env (nur für dich lesbar), entfernt ihn aus
# .git/config und nutzt ihn für git pull und den Update-Knopf.
#
# Weitere Befehle: ./einrichten.sh hilfe

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE=${WORDFLOW_SERVICE_NAME:-wordflow}
NODE_MIN=22.12
ASSUME_YES=0
REPO=rcode125/Phase-6-2.0
BRANCH=claude/new-session-uq36ca
REPO_URL="https://github.com/$REPO.git"
TOKEN_URL="https://github.com/settings/personal-access-tokens/new"

# ---------------------------------------------------------------- Ausgabe ----

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  B=$'\e[1m' D=$'\e[2m' R=$'\e[31m' G=$'\e[32m' Y=$'\e[33m' C=$'\e[36m' N=$'\e[0m'
else
  B='' D='' R='' G='' Y='' C='' N=''
fi

STEP=0
STEPS=7
step() { STEP=$((STEP + 1)); printf '\n%s[%d/%d] %s%s\n' "$B$C" "$STEP" "$STEPS" "$1" "$N"; }
ok() { printf '  %s✓%s %s\n' "$G" "$N" "$1"; }
info() { printf '  %s•%s %s\n' "$C" "$N" "$1"; }
warn() { printf '  %s!%s %s\n' "$Y" "$N" "$1"; }
fail() { printf '\n  %s✗ %s%s\n\n' "$R" "$1" "$N" >&2; exit 1; }

banner() {
  printf '%s' "$B"
  cat <<'EOF'

   __        __            _ _____ _
   \ \      / /__  _ __ __| |  ___| | _____      __
    \ \ /\ / / _ \| '__/ _` | |_  | |/ _ \ \ /\ / /
     \ V  V / (_) | | | (_| |  _| | | (_) \ V  V /
      \_/\_/ \___/|_|  \__,_|_|   |_|\___/ \_/\_/
EOF
  printf '%s\n   %sKI-gestützter Vokabeltrainer · Einrichtungsassistent%s\n' "$N" "$D" "$N"
}

# ---------------------------------------------------------------- Eingabe ----

# ask "Frage" "Standard" → Antwort in $REPLY
ask() {
  local prompt=$1 default=${2:-}
  if [[ $ASSUME_YES == 1 || ! -t 0 ]]; then REPLY=$default; return; fi
  read -r -p "  ${B}?${N} ${prompt}${default:+ ${D}[$default]${N}}: " REPLY
  REPLY=${REPLY:-$default}
}

# confirm "Frage" j|n → 0 bei Ja
confirm() {
  local prompt=$1 default=${2:-j} hint
  [[ $default == j ]] && hint='J/n' || hint='j/N'
  if [[ $ASSUME_YES == 1 || ! -t 0 ]]; then [[ $default == j ]]; return; fi
  while true; do
    read -r -p "  ${B}?${N} ${prompt} ${D}($hint)${N}: " REPLY
    REPLY=${REPLY:-$default}
    case ${REPLY,,} in
      j | ja | y | yes) return 0 ;;
      n | nein | no) return 1 ;;
    esac
  done
}

# choose "Frage" Standardnummer "Option 1" "Option 2" … → Nummer in $REPLY
choose() {
  local prompt=$1 default=$2 i
  shift 2
  printf '  %s?%s %s\n' "$B" "$N" "$prompt"
  i=1
  for option in "$@"; do
    if [[ $i == "$default" ]]; then
      printf '    %s%d)%s %s %s(Empfohlen)%s\n' "$C" "$i" "$N" "$option" "$D" "$N"
    else
      printf '    %s%d)%s %s\n' "$C" "$i" "$N" "$option"
    fi
    i=$((i + 1))
  done
  if [[ $ASSUME_YES == 1 || ! -t 0 ]]; then REPLY=$default; return; fi
  while true; do
    read -r -p "    Auswahl ${D}[$default]${N}: " REPLY
    REPLY=${REPLY:-$default}
    [[ $REPLY =~ ^[0-9]+$ && $REPLY -ge 1 && $REPLY -le $# ]] && return
    warn "Bitte eine Zahl von 1 bis $# eingeben."
  done
}

# ---------------------------------------------------------------- Helfer ----

have() { command -v "$1" >/dev/null 2>&1; }

sudo_cmd() {
  if [[ $EUID -eq 0 ]]; then "$@"; else sudo "$@"; fi
}

version_ge() { [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n1)" == "$2" ]]; }

node_ok() {
  have node || return 1
  version_ge "$(node -p 'process.versions.node' 2>/dev/null || echo 0)" "$NODE_MIN"
}

port_free() {
  local port=$1
  if have ss; then ! ss -ltnH "sport = :$port" 2>/dev/null | grep -q .; return; fi
  ! (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null
}

# IPv4 addresses in the home network (without Docker and VM bridges).
local_ips() {
  if have ip; then
    ip -4 -o addr show scope global | grep -vE ' (docker[0-9]*|br-[0-9a-f]+|veth[^ ]*|virbr[0-9]*|lxcbr[0-9]*|tailscale[0-9]*) ' |
      awk '{print $4}' | cut -d/ -f1
  elif have hostname && hostname -I >/dev/null 2>&1; then
    hostname -I | tr ' ' '\n' | grep -E '^[0-9]+\.' | grep -v '^172\.17\.' || true
  fi
}

env_get() {
  local key=$1 default=${2:-}
  if [[ -f "$ROOT/.env" ]]; then
    local line
    line=$(grep -E "^${key}=" "$ROOT/.env" | tail -n1 || true)
    [[ -n $line ]] && { printf '%s' "${line#*=}"; return; }
  fi
  printf '%s' "$default"
}

# env_set KEY Wert – ersetzt oder ergänzt eine Zeile in .env (Datei bleibt chmod 600).
env_set() {
  local key=$1 value=$2 file="$ROOT/.env" tmp
  [[ -f $file ]] || : >"$file"
  tmp=$(mktemp "$ROOT/.env.XXXXXX")
  grep -vE "^${key}=" "$file" >"$tmp" || true
  printf '%s=%s\n' "$key" "$value" >>"$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$file"
}

has_systemd() { have systemctl && [[ -d /run/systemd/system ]]; }

unit_path() {
  if [[ $1 == system ]]; then
    echo "/etc/systemd/system/$SERVICE.service"
  else
    echo "${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/$SERVICE.service"
  fi
}

# Welcher Dienst ist installiert? system, user oder leer.
installed_scope() {
  if [[ -f "$(unit_path system)" ]]; then echo system
  elif [[ -f "$(unit_path user)" ]]; then echo user
  fi
}

sctl() {
  local scope=$1
  shift
  if [[ $scope == system ]]; then sudo_cmd systemctl "$@"; else systemctl --user "$@"; fi
}

# ------------------------------------------------------------- Schritt 1 ----

check_system() {
  step "System prüfen"
  [[ "$(uname -s)" == Linux ]] || warn "Der Assistent ist für Linux gedacht ($(uname -s) erkannt)."
  [[ -f "$ROOT/package.json" && -d "$ROOT/apps/server" ]] || fail "Bitte im WordFlow-Ordner ausführen."
  [[ $EUID -eq 0 ]] && warn "Läuft als root. Besser als normaler Benutzer starten; sudo wird bei Bedarf gefragt."

  if node_ok; then
    ok "Node.js $(node -v)"
  else
    if have node; then warn "Node.js $(node -v) ist zu alt (nötig: $NODE_MIN oder neuer)."
    else warn "Node.js ist nicht installiert (nötig: $NODE_MIN oder neuer)."; fi
    install_node
    node_ok || fail "Node.js $NODE_MIN+ fehlt noch. Nach der Installation den Assistenten erneut starten."
    ok "Node.js $(node -v)"
  fi
  have npm || fail "npm fehlt (gehört normalerweise zu Node.js)."
  ok "npm $(npm -v)"

  local missing=()
  for tool in python3 make g++; do have "$tool" || missing+=("$tool"); done
  if ((${#missing[@]} == 0)); then
    ok "Build-Werkzeuge (python3, make, g++)"
  else
    warn "Es fehlen: ${missing[*]}. Meist nicht nötig (fertige Binärdateien), aber als Reserve für SQLite/Argon2."
    if have apt-get && confirm "Mit apt installieren (build-essential, python3)?" j; then
      sudo_cmd apt-get update -qq && sudo_cmd apt-get install -y -qq build-essential python3
      ok "Build-Werkzeuge installiert"
    fi
  fi
  have git && ok "git $(git --version | awk '{print $3}')" || info "git fehlt (nur für ./einrichten.sh aktualisieren nötig, der Update-Knopf geht ohne)."
  have curl || info "curl fehlt (zum Prüfen des GitHub-Tokens empfohlen)."
}

install_node() {
  choose "Wie soll Node.js 22 installiert werden?" 1 \
    "über nvm, nur für diesen Benutzer (ohne sudo)" \
    "über NodeSource mit apt, systemweit (sudo)" \
    "selbst installieren, Assistent beenden"
  case $REPLY in
    1)
      have curl || fail "curl fehlt. Bitte zuerst curl installieren."
      export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
      if [[ ! -s "$NVM_DIR/nvm.sh" ]]; then
        info "Installiere nvm …"
        curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash >/dev/null
      fi
      # shellcheck disable=SC1091
      . "$NVM_DIR/nvm.sh"
      nvm install 22 >/dev/null && nvm alias default 22 >/dev/null
      ;;
    2)
      have apt-get || fail "apt-get nicht gefunden. Bitte Node.js selbst installieren: https://nodejs.org"
      have curl || sudo_cmd apt-get install -y -qq curl
      curl -fsSL https://deb.nodesource.com/setup_22.x | sudo_cmd bash - >/dev/null
      sudo_cmd apt-get install -y -qq nodejs
      ;;
    *) fail "Bitte Node.js $NODE_MIN+ installieren (https://nodejs.org) und den Assistenten erneut starten." ;;
  esac
  hash -r
}

# ------------------------------------------------------------- Schritt 2 ----

PORT=3000
HOST=0.0.0.0

configure() {
  step "Konfiguration"
  if [[ -f "$ROOT/.env" ]]; then
    info "Es gibt schon eine .env."
    if ! confirm "Neu konfigurieren? (Nein = bestehende Werte behalten)" n; then
      PORT=$(env_get PORT 3000)
      HOST=$(env_get HOST 0.0.0.0)
      ok "Bestehende .env bleibt (Port $PORT)."
      return
    fi
  fi

  PORT=$(env_get PORT 3000)
  # Suggest the next free port (a running WordFlow service may keep its port).
  if [[ -z "$(installed_scope)" ]]; then
    while ! port_free "$PORT" && ((PORT < 65535)); do PORT=$((PORT + 1)); done
  fi
  while true; do
    ask "Port" "$PORT"
    if [[ ! $REPLY =~ ^[0-9]+$ ]] || ((REPLY < 1 || REPLY > 65535)); then
      warn "Ungültiger Port."
      continue
    fi
    PORT=$REPLY
    if port_free "$PORT" || [[ -n "$(installed_scope)" ]]; then break; fi
    warn "Port $PORT ist belegt."
    confirm "Trotzdem verwenden?" n && break
  done

  choose "Wer soll WordFlow erreichen?" 1 \
    "alle Geräte im Heimnetz (Handy, Tablet, andere PCs)" \
    "nur dieser Rechner (localhost)"
  [[ $REPLY == 1 ]] && HOST=0.0.0.0 || HOST=127.0.0.1

  ask "Ordner für Datenbank, Fotos und Schlüssel" "$(env_get DATA_DIR data)"
  local data_dir=$REPLY

  local proxy=false
  confirm "Läuft ein Reverse-Proxy mit HTTPS davor (Caddy, nginx)?" n && proxy=true

  local level
  choose "Wie viel soll im Log stehen?" 1 "info (normal)" "warn (nur Warnungen)" "debug (Fehlersuche)"
  level=$(sed -n "${REPLY}p" <<<$'info\nwarn\ndebug')

  if [[ -f "$ROOT/.env" ]]; then
    cp "$ROOT/.env" "$ROOT/.env.bak"
    info "Alte .env gesichert als .env.bak"
  fi
  # Bestehende Schlüssel und Zusatzwerte bleiben erhalten.
  local secret cors token
  secret=$(env_get APP_SECRET)
  cors=$(env_get CORS_ORIGINS)
  token=$(env_get GITHUB_TOKEN)
  cat >"$ROOT/.env" <<EOF
# Erstellt von einrichten.sh am $(date '+%Y-%m-%d %H:%M'). Vorlage: .env.example

PORT=$PORT
HOST=$HOST
DATA_DIR=$data_dir
TRUST_PROXY=$proxy
CORS_ORIGINS=$cors
LOG_LEVEL=$level

# Leer lassen: wird beim ersten Start erzeugt und in DATA_DIR/secrets.json gespeichert.
APP_SECRET=$secret

# Lese-Token für das private Repository $REPO (Updates). Ändern: ./einrichten.sh token
GITHUB_TOKEN=$token
EOF
  chmod 600 "$ROOT/.env"
  ok ".env geschrieben (Port $PORT, $([[ $HOST == 0.0.0.0 ]] && echo Heimnetz || echo nur lokal))"
}

# ------------------------------------------------------- GitHub-Zugang ----

# token_check TOKEN → 0 wenn der Token das Repository lesen darf
token_check() {
  local code
  have curl || return 0
  code=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $1" -H 'Accept: application/vnd.github+json' \
    -H 'User-Agent: WordFlow-Setup' "https://api.github.com/repos/$REPO" || true)
  case $code in
    200) return 0 ;;
    401) warn "GitHub lehnt den Token ab (ungültig oder abgelaufen)." ;;
    403 | 404) warn "Der Token darf $REPO nicht lesen. Beim Erstellen das Repository auswählen und Contents: Read-only setzen." ;;
    000 | '') warn "GitHub nicht erreichbar – Token wird ungeprüft gespeichert."; return 0 ;;
    *) warn "Unerwartete Antwort von GitHub (HTTP $code)." ;;
  esac
  return 1
}

token_help() {
  printf '  %sSo erstellst du den Token (einmalig, im Browser):%s\n' "$B" "$N"
  printf '    1. %s öffnen (als Besitzer des Repositorys angemeldet)\n' "$TOKEN_URL"
  printf '    2. Name: WordFlow-Update · Ablauf: z. B. 1 Jahr\n'
  printf '    3. Repository access: Only select repositories → %s\n' "$REPO"
  printf '    4. Permissions → Repository permissions → Contents: Read-only\n'
  printf '    5. Generate token – den Text (github_pat_…) kopieren\n'
}

# Git holt den Token über dieses Skript, statt ihn in .git/config zu speichern.
git_setup() {
  have git && [[ -d "$ROOT/.git" ]] || return 0
  git -C "$ROOT" remote get-url origin >/dev/null 2>&1 || git -C "$ROOT" remote add origin "$REPO_URL"
  # Entfernt auch einen Token aus der Clone-URL (https://x-access-token:…@github.com/…).
  git -C "$ROOT" remote set-url origin "$REPO_URL"
  git -C "$ROOT" config --local --unset-all credential.https://github.com.helper 2>/dev/null || true
  # Leerer Eintrag zuerst: andere Helfer (z. B. gh) werden für dieses Repo ignoriert.
  git -C "$ROOT" config --local --add credential.https://github.com.helper ''
  git -C "$ROOT" config --local --add credential.https://github.com.helper "!\"$ROOT/einrichten.sh\" git-credential"
  ok "git nutzt den Token aus .env (origin = $REPO_URL)"
}

github_access() {
  step "GitHub-Zugang (privates Repository $REPO)"
  local current
  current=$(env_get GITHUB_TOKEN)
  if [[ -n $current ]]; then
    if [[ $ASSUME_YES == 1 || ! -t 0 ]] || ! confirm "Es ist schon ein Token gespeichert. Neuen eintragen?" n; then
      if token_check "$current"; then ok "Token gespeichert und gültig"; else warn "Neuen Token eintragen: ./einrichten.sh token"; fi
      git_setup
      return
    fi
  elif [[ $ASSUME_YES == 1 || ! -t 0 ]]; then
    warn "Kein Token gespeichert – Updates gehen erst danach. Eintragen: ./einrichten.sh token"
    return
  fi
  token_set
}

token_set() {
  [[ -t 0 ]] || fail "Token bitte interaktiv eintragen: ./einrichten.sh token"
  token_help
  local token
  while true; do
    read -r -s -p "  ${B}?${N} Token einfügen ${D}(bleibt unsichtbar, leer = überspringen)${N}: " token
    printf '\n'
    token=${token//[[:space:]]/}
    if [[ -z $token ]]; then warn "Übersprungen. Später: ./einrichten.sh token"; return; fi
    token_check "$token" && break
    confirm "Trotzdem speichern?" n && break
  done
  env_set GITHUB_TOKEN "$token"
  ok "Token in .env gespeichert (nur für $(id -un) lesbar)"
  git_setup
  if [[ -n "$(installed_scope)" ]]; then
    info "Der laufende Dienst liest den Token erst nach einem Neustart: ./einrichten.sh dienst neustart"
  fi
}

# git credential helper: gibt den Token aus .env an git weiter.
git_credential() {
  [[ ${1:-} == get ]] || return 0
  local token
  token=$(env_get GITHUB_TOKEN)
  [[ -n $token ]] || return 0
  printf 'username=x-access-token\npassword=%s\n' "$token"
}

update_git() {
  STEPS=2
  step "Neuesten Stand holen (git pull)"
  have git || fail "git fehlt. Installieren (sudo apt install git) oder den Update-Knopf in WordFlow nutzen."
  [[ -d "$ROOT/.git" ]] || fail "Kein git-Klon. Den Update-Knopf in WordFlow nutzen."
  [[ -n "$(env_get GITHUB_TOKEN)" ]] || fail "Kein Token gespeichert. Erst: ./einrichten.sh token"
  git_setup
  git -C "$ROOT" fetch origin "$BRANCH" || fail "git fetch fehlgeschlagen – Token prüfen: ./einrichten.sh token"
  local before after scope
  before=$(git -C "$ROOT" rev-parse HEAD)
  if [[ "$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)" != "$BRANCH" ]]; then
    git -C "$ROOT" checkout "$BRANCH" 2>/dev/null || git -C "$ROOT" checkout -b "$BRANCH" --track "origin/$BRANCH"
  fi
  git -C "$ROOT" pull --ff-only origin "$BRANCH" || fail "git pull fehlgeschlagen (eigene Änderungen? git status)"
  after=$(git -C "$ROOT" rev-parse HEAD)
  if [[ $before == "$after" ]]; then ok "Schon aktuell ($(git -C "$ROOT" log -1 --format=%h))"
  else ok "Aktualisiert: ${before:0:7} → ${after:0:7}"; fi
  build
  scope=$(installed_scope)
  if [[ -n $scope ]]; then
    sctl "$scope" restart "$SERVICE"
    PORT=$(env_get PORT 3000)
    wait_ready
  fi
}

# ------------------------------------------------------------- Schritt 3 ----

build() {
  step "Installieren und bauen"
  cd "$ROOT"
  info "Abhängigkeiten installieren (npm ci) …"
  if ! NODE_ENV=development npm ci --no-audit --no-fund --progress=false --loglevel=error; then
    fail "npm ci ist fehlgeschlagen. Fehlen Build-Werkzeuge? (sudo apt install build-essential python3)"
  fi
  ok "Abhängigkeiten installiert"
  info "Web-Oberfläche und Server bauen …"
  local log
  log=$(mktemp)
  if ! npm run build >"$log" 2>&1; then
    tail -n 40 "$log" >&2
    rm -f "$log"
    fail "Build fehlgeschlagen (siehe oben)."
  fi
  rm -f "$log"
  ok "Build fertig"
}

# ------------------------------------------------------------- Schritt 4 ----

START_MODE=none

start() {
  step "Starten"
  local options=() modes=()
  if has_systemd; then
    options+=("als Systemdienst, startet mit dem Rechner (sudo)")
    modes+=(system)
    options+=("als Benutzerdienst, ohne sudo (startet nach dem Anmelden oder dauerhaft mit Linger)")
    modes+=(user)
  else
    warn "systemd nicht gefunden – kein Dienst möglich."
  fi
  options+=("jetzt einmal im Vordergrund starten (Strg+C beendet)" "nicht starten")
  modes+=(foreground none)
  local default=1
  # Without questions and without root: the user service needs no password.
  if [[ $ASSUME_YES == 1 && $EUID -ne 0 ]] && has_systemd; then default=2; fi
  choose "Wie soll WordFlow laufen?" "$default" "${options[@]}"
  START_MODE=${modes[$((REPLY - 1))]}
  case $START_MODE in
    system | user) service_install "$START_MODE" ;;
    *) ok "Kein Dienst eingerichtet." ;;
  esac
}

unit_text() {
  local scope=$1 node_bin target
  node_bin=$(command -v node)
  [[ $scope == system ]] && target=multi-user.target || target=default.target
  # Der Systemdienst läuft als der Benutzer, der ihn einrichtet.
  local account=''
  [[ $scope == system ]] && account="User=$(id -un)"$'\n'"Group=$(id -gn)"$'\n'
  cat <<EOF
[Unit]
Description=WordFlow Vokabeltrainer
Documentation=file://$ROOT/README.md
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
${account}WorkingDirectory=$ROOT
Environment=NODE_ENV=production
Environment=WORDFLOW_SERVICE=1
Environment=PATH=$(dirname "$node_bin"):/usr/local/bin:/usr/bin:/bin
ExecStart=$node_bin $ROOT/apps/server/dist/index.js
Restart=always
RestartSec=3
# Das Update-Knöpfchen baut neu und beendet den Server; systemd startet ihn neu.
KillMode=mixed
TimeoutStopSec=20

[Install]
WantedBy=$target
EOF
}

service_install() {
  local scope=$1 unit
  has_systemd || fail "systemd ist nicht verfügbar."
  [[ -f "$ROOT/apps/server/dist/index.js" ]] || fail "Erst bauen: ./einrichten.sh bauen"
  unit=$(unit_path "$scope")

  local other
  [[ $scope == system ]] && other=user || other=system
  if [[ -f "$(unit_path "$other")" ]]; then
    warn "Es gibt schon einen ${other}-Dienst – er wird entfernt, damit nicht zwei laufen."
    service_remove "$other" quiet
  fi

  local content
  content=$(unit_text "$scope")
  if [[ $scope == system ]]; then
    printf '%s\n' "$content" | sudo_cmd tee "$unit" >/dev/null
  else
    mkdir -p "$(dirname "$unit")"
    printf '%s\n' "$content" >"$unit"
  fi
  ok "Dienst angelegt: $unit"

  sctl "$scope" daemon-reload
  sctl "$scope" enable --now "$SERVICE" >/dev/null 2>&1
  sctl "$scope" restart "$SERVICE"
  ok "Dienst gestartet und aktiviert ($scope)"

  if [[ $scope == user ]] && have loginctl; then
    if [[ "$(loginctl show-user "$(id -un)" -p Linger --value 2>/dev/null)" != yes ]]; then
      if [[ $ASSUME_YES == 1 ]]; then
        info "Für Start ohne Anmeldung: sudo loginctl enable-linger $(id -un)"
      elif confirm "Dienst auch ohne Anmeldung laufen lassen (loginctl enable-linger, sudo)?" j; then
        sudo_cmd loginctl enable-linger "$(id -un)" && ok "Linger aktiviert – startet jetzt mit dem Rechner"
      else
        info "Der Dienst läuft nur, solange du angemeldet bist."
      fi
    fi
  fi
  wait_ready
}

service_remove() {
  local scope=${1:-$(installed_scope)} quiet=${2:-}
  [[ -n $scope ]] || { [[ -n $quiet ]] || warn "Kein WordFlow-Dienst installiert."; return 0; }
  sctl "$scope" disable --now "$SERVICE" >/dev/null 2>&1 || true
  if [[ $scope == system ]]; then sudo_cmd rm -f "$(unit_path system)"; else rm -f "$(unit_path user)"; fi
  sctl "$scope" daemon-reload
  [[ -n $quiet ]] || ok "Dienst entfernt ($scope). Daten in data/ bleiben erhalten."
}

wait_ready() {
  local port=${PORT:-$(env_get PORT 3000)} i
  have curl || return 0
  for i in $(seq 1 30); do
    if curl -fs "http://127.0.0.1:$port/api/health" >/dev/null 2>&1; then
      ok "WordFlow antwortet auf Port $port"
      return 0
    fi
    sleep 1
  done
  warn "Noch keine Antwort auf Port $port. Log ansehen: ./einrichten.sh dienst logs"
}

# ------------------------------------------------------------- Schritt 5 ----

firewall() {
  step "Firewall"
  if [[ ${HOST:-} == 127.0.0.1 ]]; then ok "Nur lokal erreichbar – nichts zu tun."; return; fi
  # Detected without sudo; sudo is only needed to open the port.
  if have ufw && have systemctl && systemctl is-active --quiet ufw; then
    if confirm "ufw ist aktiv. Port $PORT/tcp freigeben?" j; then
      sudo_cmd ufw allow "$PORT/tcp" comment WordFlow >/dev/null && ok "Port $PORT freigegeben"
    fi
  elif have firewall-cmd && have systemctl && systemctl is-active --quiet firewalld; then
    if confirm "firewalld ist aktiv. Port $PORT/tcp freigeben?" j; then
      sudo_cmd firewall-cmd --permanent --add-port="$PORT/tcp" >/dev/null
      sudo_cmd firewall-cmd --reload >/dev/null && ok "Port $PORT freigegeben"
    fi
  else
    ok "Keine aktive Firewall erkannt."
  fi
}

# ------------------------------------------------------------- Schritt 6 ----

summary() {
  step "Fertig"
  local ip
  printf '\n  %sWordFlow öffnen:%s\n' "$B" "$N"
  printf '    http://localhost:%s\n' "$PORT"
  if [[ $HOST != 127.0.0.1 ]]; then
    while read -r ip; do [[ -n $ip ]] && printf '    http://%s:%s  %s(Heimnetz, auch für die Android-App)%s\n' "$ip" "$PORT" "$D" "$N"; done < <(local_ips)
  fi
  printf '\n  %sErste Anmeldung:%s admin@wordflow.com / admin  %s→ Passwort danach ändern!%s\n' "$B" "$N" "$Y" "$N"
  printf '  Dann unter Einstellungen → KI die KI einrichten und unter Accounts die Lernenden anlegen.\n'
  printf '\n  %sWichtig:%s data/secrets.json sichern – ohne sie sind gespeicherte API-Schlüssel verloren.\n' "$B" "$N"
  printf '\n  %sNützliche Befehle:%s\n' "$B" "$N"
  printf '    ./einrichten.sh dienst status    Läuft WordFlow?\n'
  printf '    ./einrichten.sh dienst logs      Log live ansehen\n'
  printf '    ./einrichten.sh dienst neustart  Neu starten\n'
  printf '    ./einrichten.sh hilfe            Alle Befehle\n\n'
}

# ---------------------------------------------------------------- Docker ----

docker_setup() {
  STEPS=3
  step "Docker prüfen"
  have docker || fail "Docker fehlt. Installation: https://docs.docker.com/engine/install/"
  docker compose version >/dev/null 2>&1 || fail "Docker Compose (docker compose) fehlt."
  ok "$(docker --version)"

  step "Optionen"
  local profiles=()
  confirm "Zusätzlich Ollama für lokale KI starten?" n && profiles+=(--profile ollama)
  if confirm "Zusätzlich Caddy für HTTPS (eigene Domain nötig)?" n; then
    profiles+=(--profile https)
    warn "Domain in deploy/Caddyfile eintragen und TRUST_PROXY=true in .env setzen."
  fi
  [[ -f "$ROOT/.env" ]] || cp "$ROOT/.env.example" "$ROOT/.env"

  step "Starten"
  (cd "$ROOT" && docker compose "${profiles[@]}" up -d --build)
  PORT=3000 HOST=0.0.0.0
  wait_ready
  printf '\n  WordFlow: http://localhost:3000 · Anmeldung admin@wordflow.com / admin\n'
  printf '  Log: docker compose logs -f wordflow · Stoppen: docker compose down\n\n'
}

# ---------------------------------------------------------------- Befehle ----

wizard() {
  banner
  printf '\n  Willkommen! Der Assistent richtet WordFlow auf diesem Rechner ein.\n'
  printf '  %sEnter übernimmt jeweils den Vorschlag in [Klammern]. Strg+C bricht ab.%s\n\n' "$D" "$N"
  choose "Wie soll WordFlow installiert werden?" 1 \
    "direkt mit Node.js (empfohlen für Raspberry Pi, Heimserver, PC)" \
    "mit Docker Compose"
  if [[ $REPLY == 2 ]]; then docker_setup; return; fi

  check_system
  configure
  github_access
  build
  start
  firewall
  summary
  if [[ $START_MODE == foreground ]]; then
    info "Starte WordFlow im Vordergrund … (Strg+C beendet)"
    cd "$ROOT" && exec env NODE_ENV=production node apps/server/dist/index.js
  fi
}

service_cmd() {
  local action=${1:-status} scope
  scope=$(installed_scope)
  case $action in
    installieren | install)
      local s=${2:-}
      if [[ -z $s ]]; then
        choose "Welcher Dienst?" 1 "Systemdienst (sudo)" "Benutzerdienst (ohne sudo)"
        [[ $REPLY == 1 ]] && s=system || s=user
      fi
      PORT=$(env_get PORT 3000)
      service_install "$s"
      ;;
    entfernen | remove | uninstall) service_remove "${2:-$scope}" ;;
    anzeigen | show) unit_text "${2:-${scope:-system}}" ;;
    status)
      [[ -n $scope ]] || { warn "Kein WordFlow-Dienst installiert. Einrichten: ./einrichten.sh dienst installieren"; return; }
      sctl "$scope" status "$SERVICE" --no-pager || true
      ;;
    logs | log)
      [[ -n $scope ]] || fail "Kein WordFlow-Dienst installiert."
      if [[ $scope == system ]]; then sudo_cmd journalctl -u "$SERVICE" -f -n 100
      else journalctl --user -u "$SERVICE" -f -n 100; fi
      ;;
    neustart | restart) [[ -n $scope ]] || fail "Kein WordFlow-Dienst installiert."; sctl "$scope" restart "$SERVICE"; PORT=$(env_get PORT 3000); wait_ready ;;
    stopp | stop) [[ -n $scope ]] || fail "Kein WordFlow-Dienst installiert."; sctl "$scope" stop "$SERVICE"; ok "Gestoppt" ;;
    start) [[ -n $scope ]] || fail "Kein WordFlow-Dienst installiert."; sctl "$scope" start "$SERVICE"; PORT=$(env_get PORT 3000); wait_ready ;;
    *) fail "Unbekannte Dienst-Aktion: $action (installieren, entfernen, anzeigen, status, logs, start, stopp, neustart)" ;;
  esac
}

help() {
  banner
  cat <<EOF

  ${B}Aufruf:${N} ./einrichten.sh [befehl] [-j]

  ${B}Befehle${N}
    ${C}(ohne)${N}                      Assistent: prüfen, konfigurieren, bauen, starten
    ${C}dienst installieren${N} [system|user]  WordFlow als Systemdienst einrichten
    ${C}dienst entfernen${N}            Dienst entfernen (Daten bleiben)
    ${C}dienst anzeigen${N} [system|user]      Dienstdatei nur anzeigen
    ${C}dienst status${N}               Läuft der Dienst?
    ${C}dienst logs${N}                 Log live ansehen
    ${C}dienst start|stopp|neustart${N}
    ${C}token${N}                       GitHub-Zugangs-Token eintragen oder ersetzen
    ${C}aktualisieren${N}               git pull + Build + Dienst neu starten
    ${C}bauen${N}                       npm ci + Build (z. B. nach eigenen Änderungen)
    ${C}docker${N}                      Mit Docker Compose einrichten
    ${C}hilfe${N}                       Diese Hilfe

  ${B}Optionen${N}
    ${C}-j, --ja${N}                    Keine Fragen, überall den Vorschlag nehmen

  ${B}Privates Repository${N} ($REPO)
    Updates brauchen einen Lese-Token. Erstellen unter
    $TOKEN_URL
    (Only select repositories → $REPO, Contents: Read-only), dann ./einrichten.sh token

EOF
}

main() {
  local args=()
  for a in "$@"; do
    case $a in
      -j | --ja | -y | --yes) ASSUME_YES=1 ;;
      *) args+=("$a") ;;
    esac
  done
  set -- "${args[@]+"${args[@]}"}"
  case ${1:-} in
    '') wizard ;;
    dienst | service) shift; service_cmd "$@" ;;
    bauen | build) STEPS=1; build ;;
    token) STEPS=1; step "GitHub-Zugang"; token_set ;;
    aktualisieren | update | pull) update_git ;;
    git-credential) git_credential "${2:-}" ;;
    docker) banner; docker_setup ;;
    hilfe | help | -h | --help) help ;;
    *) fail "Unbekannter Befehl: $1 (./einrichten.sh hilfe)" ;;
  esac
}

main "$@"
