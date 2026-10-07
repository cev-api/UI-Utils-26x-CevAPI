"""Check a built mod's direct Minecraft member references against runtime jars.

Usage: python scripts/check_mc_linkage.py MOD.jar MINECRAFT.jar [MINECRAFT.jar ...]
Reflection and behavior still require the Gradle regression checks and game QA.
"""
import struct
import sys
import zipfile
import re
import subprocess
from functools import lru_cache


def read_class(data):
    offset = 8

    def take(fmt):
        nonlocal offset
        result = struct.unpack_from(">" + fmt, data, offset)
        offset += struct.calcsize(">" + fmt)
        return result[0] if len(result) == 1 else result

    pool = [None] * take("H")
    index = 1
    while index < len(pool):
        tag = take("B")
        if tag == 1:
            length = take("H")
            pool[index] = (tag, data[offset:offset + length].decode("utf-8", errors="replace"))
            offset += length
        elif tag in (3, 4):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, take("H"))
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, *take("HH"))
        elif tag == 15:
            pool[index] = (tag, *take("BH"))
        else:
            raise ValueError(f"Unknown constant pool tag {tag}")
        index += 1

    def utf(index):
        return pool[index][1]

    def class_name(index):
        return utf(pool[index][1]) if index else None

    take("H")  # access flags
    this_name = class_name(take("H"))
    parents = [class_name(take("H"))]
    parents += [class_name(take("H")) for _ in range(take("H"))]

    def skip_attributes():
        nonlocal offset
        for _ in range(take("H")):
            take("H")
            length = take("I")
            offset += length

    members = set()
    for kind in (9, 10):  # field and method declarations
        for _ in range(take("H")):
            take("H")
            members.add((kind, utf(take("H")), utf(take("H"))))
            skip_attributes()
    references = set()
    for entry in pool:
        if entry and entry[0] in (9, 10, 11):
            kind, owner, name_type = entry
            _, name, descriptor = pool[name_type]
            references.add((9 if kind == 9 else 10, class_name(owner), utf(name), utf(descriptor)))
    return this_name, parents, members, references


@lru_cache(None)
def read_jdk_class(name):
    result = subprocess.run(["javap", "-s", "-private", name.replace("/", ".")],
                            capture_output=True, text=True, check=True)
    lines = result.stdout.splitlines()
    header = next(line for line in lines if "{" in line)
    while re.search(r"<[^<>]*>", header):
        header = re.sub(r"<[^<>]*>", "", header)
    parents = []
    for match in re.finditer(r"(?:extends|implements) (.*?)(?= implements | \{|$)", header):
        parents.extend(p.strip().replace(".", "/") for p in match[1].split(","))
    if not parents and name != "java/lang/Object" and " class " in header:
        parents.append("java/lang/Object")
    members = set()
    for previous, line in zip(lines, lines[1:]):
        if "descriptor:" not in line:
            continue
        descriptor = line.split("descriptor:", 1)[1].strip()
        if "(" in previous:
            member = previous.split("(", 1)[0].split()[-1]
            members.add((10, member.rsplit(".", 1)[-1], descriptor))
        else:
            members.add((9, previous.strip().rstrip(";").split()[-1], descriptor))
    return name, parents, members, set()


def verify(mod_path, runtime_path):
    with zipfile.ZipFile(mod_path) as mod, zipfile.ZipFile(runtime_path) as runtime:
        names = set(runtime.namelist())

        @lru_cache(None)
        def load(name):
            path = name + ".class"
            if path in names:
                return read_class(runtime.read(path))
            if name.startswith("java/"):
                return read_jdk_class(name)
            return None

        @lru_cache(None)
        def has_member(owner, kind, name, descriptor):
            info = load(owner)
            if info is None:
                return False
            _, parents, members, _ = info
            if (kind, name, descriptor) in members:
                return True
            if name == "<init>":
                return False
            return any(has_member(p, kind, name, descriptor) for p in parents if p)

        failures = set()
        checked = set()
        for path in mod.namelist():
            if not path.endswith(".class"):
                continue
            _, _, _, refs = read_class(mod.read(path))
            for kind, owner, name, descriptor in refs:
                if not owner.startswith("net/minecraft/"):
                    continue
                ref = (kind, owner, name, descriptor)
                checked.add(ref)
                # Missing owner classes are errors even if their methods might be inherited.
                if load(owner) is None or not has_member(owner, kind, name, descriptor):
                    failures.add(ref)
        for _, owner, name, descriptor in sorted(failures):
            print(f"MISSING {owner}.{name}{descriptor}")
        print(f"{runtime_path}: {len(checked)} Minecraft member references, {len(failures)} missing")
        return not failures


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    results = [verify(sys.argv[1], runtime) for runtime in sys.argv[2:]]
    raise SystemExit(0 if all(results) else 1)
