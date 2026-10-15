"""Install a private Android build toolchain under ignored .tools/."""
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)


def fetch(url, path, checksum=None):
    if not path.exists():
        print(f'Downloading {path.name}', flush=True)
        request = urllib.request.Request(url, headers={'User-Agent': 'accountingApp-build'})
        with urllib.request.urlopen(request, timeout=180) as response, path.open('wb') as target:
            shutil.copyfileobj(response, target)
    if checksum and hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
        raise RuntimeError(f'Checksum mismatch: {path.name}; remove the corrupt archive and retry')


def extract(archive, destination):
    with zipfile.ZipFile(archive) as source:
        base = destination.resolve()
        for name in source.namelist():
            target = (base / name).resolve()
            if not target.is_relative_to(base):
                raise RuntimeError('Archive contains an unsafe path')
        source.extractall(destination)


if not (TOOLS / 'jdk' / 'bin' / 'java.exe').exists():
    release_url = 'https://api.github.com/repos/adoptium/temurin17-binaries/releases/latest'
    request = urllib.request.Request(release_url, headers={'User-Agent': 'accountingApp-build'})
    with urllib.request.urlopen(request, timeout=60) as response:
        assets = json.load(response)['assets']
    asset = next(item for item in assets if item['name'].startswith('OpenJDK17U-jdk_x64_windows_hotspot_') and item['name'].endswith('.zip'))
    with urllib.request.urlopen(asset['browser_download_url'] + '.sha256.txt', timeout=60) as response:
        checksum = response.read().decode().split()[0]
    archive = TOOLS / 'jdk17.zip'
    fetch(asset['browser_download_url'], archive, checksum)
    unpack = TOOLS / 'jdk-unpack'
    extract(archive, unpack)
    shutil.move(str(next(unpack.iterdir())), str(TOOLS / 'jdk'))

sdk = TOOLS / 'android-sdk'
manager = sdk / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager.bat'
if not manager.exists():
    archive = TOOLS / 'android-commandline.zip'
    fetch('https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip', archive)
    unpack = TOOLS / 'sdk-unpack'
    extract(archive, unpack)
    (sdk / 'cmdline-tools').mkdir(parents=True, exist_ok=True)
    shutil.move(str(unpack / 'cmdline-tools'), str(sdk / 'cmdline-tools' / 'latest'))

version = '8.11.1'
wrapper = ROOT / 'gradle' / 'wrapper'
wrapper.mkdir(parents=True, exist_ok=True)
for remote, local in [('gradle/wrapper/gradle-wrapper.jar', wrapper / 'gradle-wrapper.jar'), ('gradlew', ROOT / 'gradlew'), ('gradlew.bat', ROOT / 'gradlew.bat')]:
    fetch(f'https://raw.githubusercontent.com/gradle/gradle/v{version}/{remote}', local)
with urllib.request.urlopen(f'https://services.gradle.org/distributions/gradle-{version}-bin.zip.sha256', timeout=60) as response:
    checksum = response.read().decode().strip()
(wrapper / 'gradle-wrapper.properties').write_text(
    'distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\n'
    f'distributionUrl=https\\://services.gradle.org/distributions/gradle-{version}-bin.zip\n'
    f'distributionSha256Sum={checksum}\nnetworkTimeout=180000\nvalidateDistributionUrl=true\n'
    'zipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n', encoding='utf-8')
(ROOT / 'local.properties').write_text('sdk.dir=' + str(sdk).replace('\\', '/').replace(':', '\\:') + '\n', encoding='utf-8')
print('JDK 17 and Android command-line tools ready; install SDK packages next.', flush=True)
