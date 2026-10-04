import unittest
from java8_sources import backport


class Java8SourcesCheck(unittest.TestCase):
    def test_record_validation_and_value_semantics(self):
        result = backport('public record ItemState(int level, String id) { public ItemState { if (level < 1) throw new IllegalArgumentException(); } }')
        self.assertIn('public static final class ItemState', result)
        self.assertIn('public ItemState(int level, String id)', result)
        self.assertIn('if (level < 1) throw', result)
        self.assertIn('java.util.Objects.equals(id, other.id)', result)

    def test_switch_array_and_throw(self):
        result = backport('Item[] pattern = switch (level) { case 1 -> new Item[]{A, B}; default -> new Item[]{C, D};\n };')
        self.assertIn('pattern = new Item[]{A, B}; break;', result)
        result = backport('return switch (level) { case 1 -> first; default -> throw new IllegalArgumentException(); };')
        self.assertIn('case 1: return first;', result)
        self.assertIn('default: throw new IllegalArgumentException();', result)

    def test_text_block_and_unknown_syntax(self):
        self.assertIn('"a\\nb\\n"', backport('String value = """\n  a\n  b\n  """;'))
        with self.assertRaises(ValueError):
            backport('var unknown = something();')


if __name__ == '__main__':
    unittest.main()
