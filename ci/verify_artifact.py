"""Check loader metadata, entrypoints and mixin packaging in installable JARs."""
import argparse
import json
import tomllib
import zipfile
from pathlib import Path


def verify(path, minecraft, loader):
    assert f"-{minecraft}-{loader}-" in path.name, path.name
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        config = json.loads(jar.read("flightblock.mixins.json"))
        if "refmap" in config:
            assert config["refmap"] in names, "Missing Mixin reference map"
        for mixin in config["mixins"]:
            assert f"{config['package'].replace('.', '/')}/{mixin}.class" in names, mixin
        if loader in ("fabric", "quilt"):
            metadata = json.loads(jar.read("fabric.mod.json"))
            assert metadata["depends"]["minecraft"] == minecraft
            for entry in metadata["entrypoints"]["main"]:
                assert entry.replace(".", "/") + ".class" in names
            assert any("PlayerAbilityLib" in dep["file"] and dep["file"] in names for dep in metadata["jars"])
        else:
            descriptor = "META-INF/" + ("neoforge.mods.toml" if loader == "neoforge" else "mods.toml")
            metadata = tomllib.loads(jar.read(descriptor).decode())
            assert metadata["mods"][0]["modId"] == "flightblock"
            assert "${" not in metadata["mods"][0]["version"]
            assert metadata["mods"][0]["displayTest"] == "IGNORE_ALL_VERSION"
            dependencies = metadata["dependencies"]["flightblock"]
            minecraft_dependency = next(dep for dep in dependencies if dep["modId"] == "minecraft")
            assert minecraft_dependency["versionRange"] == f"[{minecraft}]"
            entry = "NeoForgeEntrypoint" if loader == "neoforge" else "ForgeEntrypoint"
            assert f"dev/flightblock/platform/{entry}.class" in names
            assert "fabric.mod.json" not in names
            assert "dev/flightblock/PalFlightAccess.class" not in names
            if loader == "forge":
                assert b"MixinConfigs: flightblock.mixins.json" in jar.read("META-INF/MANIFEST.MF")
                if minecraft in ("1.21.8", "1.21.4", "1.21.1", "1.20.6", "1.20.4"):
                    nested = json.loads(jar.read("META-INF/jarjar/metadata.json"))["jars"]
                    assert any("mixinextras" in dep["identifier"]["artifact"] and dep["path"] in names for dep in nested)
            else:
                assert metadata["mixins"][0]["config"] == "flightblock.mixins.json"
    print(f"ARTIFACT_OK: {minecraft}/{loader}/{path.name}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("minecraft")
    parser.add_argument("loader", choices=["fabric", "quilt", "forge", "neoforge"])
    args = parser.parse_args()
    jars = list(args.directory.glob("*.jar"))
    assert jars, "No installable JARs found"
    for path in jars:
        verify(path, args.minecraft, args.loader)
