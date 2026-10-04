"""Backport the shared sources' known Java language constructs for Minecraft 1.16.5.

This runs on generated sources only. The normal sources stay readable, and Actions
compiles the result with --release 8 and runs the same gameplay assertions.
"""
import json
import re
import sys
import textwrap
from pathlib import Path


def record(match, source):
    name, fields = match.groups()
    fields = [tuple(field.strip().split()) for field in fields.split(",")]
    args = ", ".join(f"{kind} {field}" for kind, field in fields)
    assignments = " ".join(f"this.{field} = {field};" for _, field in fields)
    members = "\n" + "\n".join(
        f"private final {kind} {field}; public {kind} {field}() {{ return {field}; }}"
        for kind, field in fields)
    if not re.search(rf"public {name}\s*\{{", source):
        members += f"\npublic {name}({args}) {{ {assignments} }}\n"
    equal = " && ".join(f"java.util.Objects.equals({field}, other.{field})" for _, field in fields)
    members += f"\n@Override public boolean equals(Object value) {{ if (!(value instanceof {name})) return false; {name} other = ({name})value; return {equal}; }}\n"
    members += "@Override public int hashCode() { return java.util.Objects.hash(" + ", ".join(field for _, field in fields) + "); }\n"
    return f"public {'static ' if name != 'Config' else ''}final class {name} {{" + members, args, assignments


def switch(kind, name, selector, body):
    returning = kind is None
    prefix = "" if returning else f"{kind} {name};\n"
    cases = re.findall(r"(?:case (\d+)|default) -> (.*?);(?=\s*(?:case|default|$))", body, re.S)
    if not cases or not any(not key for key, _ in cases):
        raise ValueError("Unrecognized switch expression")
    result = prefix + f"switch ({selector}) {{\n"
    for key, expression in cases:
        label = f"case {key}" if key else "default"
        expression = expression.strip()
        action = expression if expression.startswith("throw ") else ("return " + expression if returning else name + " = " + expression)
        result += f"{label}: {action};" + ("" if returning or expression.startswith("throw ") else " break;") + "\n"
    return result + "}"


def switches(source):
    pattern = re.compile(r"(?:(\w+(?:\[\])?) (\w+) = |return )switch \((.*?)\) \{", re.S)
    while match := pattern.search(source):
        depth, quote, escaped = 1, None, False
        end = match.end()
        while depth and end < len(source):
            char = source[end]
            if quote:
                if escaped: escaped = False
                elif char == "\\": escaped = True
                elif char == quote: quote = None
            elif char in ('"', "'"): quote = char
            elif char == '{': depth += 1
            elif char == '}': depth -= 1
            end += 1
        if depth or source[end] != ';': raise ValueError('Unclosed switch expression')
        replacement = switch(*match.groups(), source[match.end():end - 1])
        source = source[:match.start()] + replacement + source[end + 1:]
    return source


def backport(source):
    if "class GameModeMixin" in source:
        source = source.replace('@WrapMethod(method = "changeGameModeForPlayer")', '@WrapMethod(method = "setGameModeForPlayer(Lnet/minecraft/world/level/GameType;)V")')
        source = source.replace('private boolean flightblock$mode(GameType type, Operation<Boolean> original)', 'private void flightblock$mode(GameType type, Operation<Void> original)')
        source = source.replace('boolean changed = original.call(type);', 'original.call(type);').replace('if (changed && FlightBlock.INSTANCE', 'if (FlightBlock.INSTANCE').replace('        return changed;\n', '')
    for match in list(re.finditer(r"public record (\w+)\(([^)]+)\)\s*\{", source)):
        declaration, args, assignments = record(match, source)
        name = match.group(1)
        source = source.replace(match.group(), declaration)
        source = re.sub(rf"public {name}\s*\{{", f"public {name}({args}) {{ {assignments}", source)
    source = re.sub(r'"""\n(.*?)\n\s*"""', lambda m: json.dumps(textwrap.dedent(m.group(1)) + "\n", ensure_ascii=False), source, flags=re.S)
    source = switches(source)
    source = source.replace(".toList()", ".collect(java.util.stream.Collectors.toList())")
    source = source.replace("java.util.Set.of(", "dev.flightblock.LegacyJava.set(")
    for old, new in [("List.of(", "dev.flightblock.LegacyJava.list("), ("Set.of(", "dev.flightblock.LegacyJava.set("), ("Set.copyOf(", "dev.flightblock.LegacyJava.copySet(")]:
        source = source.replace(old, new)
    source = source.replace("Files.readString(path, StandardCharsets.UTF_8)", "new String(Files.readAllBytes(path), StandardCharsets.UTF_8)")
    source = source.replace("Files.writeString(configPath, Config.DEFAULT_JSON)", "Files.write(configPath, Config.DEFAULT_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8))")
    source = source.replace("durations.keySet()", "LegacyJava.keys(durations)").replace("radii.keySet()", "LegacyJava.keys(radii)")
    source = source.replace("var level = mod.server.getLevel(", "net.minecraft.server.level.ServerLevel level = mod.server.getLevel(")
    source = source.replace("var entity = p.drop(", "net.minecraft.world.entity.item.ItemEntity entity = p.drop(")
    source = source.replace("if (!(player instanceof ServerPlayer p) ||", "ServerPlayer p = player instanceof ServerPlayer ? (ServerPlayer)player : null;\n            if (p == null ||")
    source = source.replace("if (!(ctx.getLevel() instanceof ServerLevel level) ||", "ServerLevel level = ctx.getLevel() instanceof ServerLevel ? (ServerLevel)ctx.getLevel() : null;\n        if (level == null ||")
    source = source.replace("ctx.getPlayer() instanceof ServerPlayer p ? p : null", "ctx.getPlayer() instanceof ServerPlayer ? (ServerPlayer)ctx.getPlayer() : null")
    source = source.replace("if (player instanceof ServerPlayer p && FlightBlock.INSTANCE != null) FlightBlock.INSTANCE.refresh(p);", "if (player instanceof ServerPlayer && FlightBlock.INSTANCE != null) FlightBlock.INSTANCE.refresh((ServerPlayer)player);")
    source = re.sub(r"if \((event\.[\w]+\(\)) instanceof (\w+) (\w+)\) \{", lambda m: f"if ({m[1]} instanceof {m[2]}) {{ {m[2]} {m[3]} = ({m[2]}){m[1]};", source)
    source = source.replace("player.getAbilities()", "player.abilities").replace("p.getAbilities()", "p.abilities")
    source = source.replace("p.getInventory()", "p.inventory").replace("player.getInventory()", "player.inventory")
    source = source.replace("stack.is(Items.TARGET)", "(stack.getItem() == Items.TARGET)")
    source = source.replace("input.getItem(i).is(pattern[i])", "(input.getItem(i).getItem() == pattern[i])")
    source = source.replace(".setTarget(p.getUUID())", ".setOwner(p.getUUID())")
    source = source.replace(".discard()", ".remove()").replace("glow.isRemoved()", "glow.removed").replace("old.isRemoved()", "old.removed")
    source = source.replace(".isCurrentlyGlowing()", ".isGlowing()").replace("setGlowingTag(", "setGlowing(")
    source = source.replace("boolean shouldBeSaved()", "boolean save(net.minecraft.nbt.CompoundTag tag)")
    source = source.replace("JsonParser.parseString(", "new JsonParser().parse(")
    if re.search(r"\bvar\s+\w+\s*=|\brecord\s+\w+|instanceof \w+ \w+", source):
        raise ValueError("Unhandled post-Java-8 language syntax")
    return source


if __name__ == "__main__":
    for path in Path(sys.argv[1]).rglob("*.java"):
        path.write_text(backport(path.read_text(encoding="utf-8")), encoding="utf-8")
