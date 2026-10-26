#!/usr/bin/env python3
"""Validate Maven artifacts, build a Central bundle, and optionally publish it."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import time
from urllib import request
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)
VERSION = PROPERTIES['VERSION_NAME']
GROUP = PROPERTIES['GROUP']
MODULES = ('core', 'glass', 'ui', 'scaffold', 'preference', 'icons')


def credentials(signing=False):
    names = ['MAVEN_CENTRAL_USERNAME', 'MAVEN_CENTRAL_PASSWORD']
    if signing:
        names.append('MAVEN_SIGNING_KEY')
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise SystemExit('Configure release credentials: ' + ', '.join(missing))
    tag = os.environ.get('GITHUB_REF', '')
    if (os.environ.get('GITHUB_ACTIONS') == 'true' or tag.startswith('refs/tags/')) and tag != f'refs/tags/v{VERSION}':
        raise SystemExit(f'Tag must match VERSION_NAME={VERSION}')


def bundle(allow_unsigned=False):
    staging = ROOT / 'build/maven'
    payload = []
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    for module in MODULES:
        artifact = f'glasmio-{module}'
        folder = staging / GROUP.replace('.', '/') / artifact / VERSION
        stem = f'{artifact}-{VERSION}'
        for suffix in ('.aar', '.pom', '.module', '-sources.jar', '-javadoc.jar'):
            path = folder / (stem + suffix)
            if not path.is_file() or path.stat().st_size == 0:
                raise SystemExit(f'Missing staged artifact: {path}')
            if suffix == '.pom':
                pom = ET.parse(path)
                for key in ('name', 'description', 'url', 'licenses', 'developers', 'scm'):
                    if pom.find('m:' + key, ns) is None:
                        raise SystemExit(f'Missing POM field {key}: {path}')
                for key, expected in [('groupId', GROUP), ('artifactId', artifact), ('version', VERSION)]:
                    if pom.findtext('m:' + key, namespaces=ns) != expected:
                        raise SystemExit(f'Incorrect POM {key}: {path}')
            if suffix == '.module':
                metadata = json.loads(path.read_text())
                component = metadata['component']
                if any(component.get(key) != expected for key, expected in [('group', GROUP), ('module', artifact), ('version', VERSION)]):
                    raise SystemExit(f'Incorrect Gradle component metadata: {path}')
            if suffix in ('.aar', '-sources.jar', '-javadoc.jar'):
                with zipfile.ZipFile(path) as archive:
                    names = archive.namelist()
                    if any(name.endswith(('.ttf', '.otf')) for name in names):
                        raise SystemExit(f'Host font included in published artifact: {path}')
                    if suffix == '.aar':
                        with zipfile.ZipFile(io.BytesIO(archive.read('classes.jar'))) as classes:
                            names = classes.namelist()
                    if suffix != '-javadoc.jar':
                        prefix = f'META-INF/glasmio/{artifact}/'
                        for legal in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'licenses/Apache-2.0.txt'):
                            if prefix + legal not in names:
                                raise SystemExit(f'Missing packaged notice {legal}: {path}')
                    if suffix == '-sources.jar' and module == 'glass':
                        for source in ('native/liquid_menu_sdf.cpp', 'native/CMakeLists.txt'):
                            if source not in names:
                                raise SystemExit(f'Missing native source {source}: {path}')
                    if suffix == '-javadoc.jar' and not any(name.endswith('.html') for name in names):
                        raise SystemExit(f'Empty API documentation: {path}')
            payload.append(path)
            signature = path.with_name(path.name + '.asc')
            if signature.exists():
                payload.append(signature)
            elif not allow_unsigned:
                raise SystemExit(f'Missing signature: {signature}')
    target = ROOT / 'build/glasmio-central.zip'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in payload:
            relative = path.relative_to(staging).as_posix()
            content = path.read_bytes()
            archive.writestr(relative, content)
            if not relative.endswith('.asc'):
                for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
                    archive.writestr(relative + '.' + algorithm, hashlib.new(algorithm, content).hexdigest())
    print(f'Validated {len(MODULES)} modules: {target}')
    return target


def upload(path):
    credentials()
    token = base64.b64encode((os.environ['MAVEN_CENTRAL_USERNAME'] + ':' + os.environ['MAVEN_CENTRAL_PASSWORD']).encode()).decode()
    headers = {'Authorization': 'Bearer ' + token}
    boundary = uuid.uuid4().hex
    content = (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode() + path.read_bytes() + f'\r\n--{boundary}--\r\n'.encode())
    headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
    endpoint = 'https://central.sonatype.com/api/v1/publisher'
    req = request.Request(endpoint + '/upload?publishingType=AUTOMATIC', data=content, headers=headers)
    with request.urlopen(req, timeout=120) as response:
        deployment = response.read().decode().strip().strip('"')
    print('Central deployment: ' + deployment)
    for _ in range(80):
        req = request.Request(endpoint + '/status?id=' + deployment, data=b'', headers={'Authorization': 'Bearer ' + token})
        with request.urlopen(req, timeout=60) as response:
            result = json.load(response)
        state = result.get('deploymentState')
        if state == 'PUBLISHED':
            print('Published Glasmio ' + VERSION)
            return
        if state == 'FAILED':
            raise SystemExit(json.dumps(result.get('errors', result), indent=2))
        time.sleep(15)
    raise SystemExit('Central deployment is still pending: ' + deployment)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-unsigned', action='store_true', help='Validate local development artifacts only')
    parser.add_argument('--check-credentials', action='store_true')
    parser.add_argument('--upload', action='store_true')
    args = parser.parse_args()
    if args.check_credentials:
        credentials(signing=True)
    else:
        if args.upload and args.allow_unsigned:
            parser.error('Unsigned bundles cannot be uploaded')
        target = bundle(args.allow_unsigned)
        if args.upload:
            upload(target)
