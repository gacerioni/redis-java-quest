#!/usr/bin/env python3
"""Exercise the Unix launcher in an isolated project with controlled Java/build exits."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]


class LauncherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="quest-launcher-")
        self.root = Path(self.temp.name)
        shutil.copy2(REPO / "quest", self.root / "quest")
        (self.root / "target").mkdir()
        (self.root / "src").mkdir()
        (self.root / "bin").mkdir()
        (self.root / "pom.xml").write_text("<project/>")
        (self.root / "src/Lab.java").write_text("// example")
        (self.root / "target/quest.jar").touch()
        for path in (self.root / "pom.xml", self.root / "src/Lab.java"):
            os.utime(path, (100, 100))
        os.utime(self.root / "target/quest.jar", (200, 200))
        java = self.root / "bin/java"
        java.write_text('#!/bin/sh\nprintf "java %s\\n" "$*" >> "$TRACE"\nif [ "$3" = "${FAIL_COMMAND:-}" ]; then exit "${FAIL_CODE:-1}"; fi\nif [ "$3" = "exercise" ]; then exit "${EXERCISE_CODE:-0}"; fi\n')
        java.chmod(0o755)
        build = self.root / "mvnw"
        build.write_text('#!/bin/sh\nprintf "build\\n" >> "$TRACE"\n[ "${BUILD_CODE:-0}" = "0" ] || exit "$BUILD_CODE"\ntouch target/quest.jar\n')
        build.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.root / "bin") + os.pathsep + os.environ["PATH"], TRACE=str(self.root / "trace"))
        self.env.pop("QUEST_REBUILD", None)

    def tearDown(self):
        self.temp.cleanup()

    def run_quest(self, *args, **env):
        result = subprocess.run(["bash", str(self.root / "quest"), *args], env=dict(self.env, **env), capture_output=True, text=True)
        trace = (self.root / "trace").read_text().splitlines() if (self.root / "trace").exists() else []
        return result.returncode, trace

    def test_reuses_current_build(self):
        code, trace = self.run_quest("list")
        self.assertEqual(code, 0)
        self.assertEqual(trace, ["java -jar target/quest.jar list"])

    def test_builds_after_source_change(self):
        os.utime(self.root / "src/Lab.java", (300, 300))
        code, trace = self.run_quest("run", "100-02", "jedis")
        self.assertEqual(code, 0)
        self.assertEqual(trace[0], "build")

    def test_builds_after_resource_change(self):
        (self.root / "src/world.json").write_text("{}")
        code, trace = self.run_quest("seed")
        self.assertEqual(code, 0)
        self.assertEqual(trace[0], "build")

    def test_explicit_rebuild(self):
        code, trace = self.run_quest("list", QUEST_REBUILD="1")
        self.assertEqual(code, 0)
        self.assertEqual(trace[0], "build")

    def test_solution_rebuilds_then_executes_and_verifies(self):
        code, trace = self.run_quest("solve", "101-01", "--yes", FAIL_COMMAND="solve", FAIL_CODE="5")
        self.assertEqual(code, 0)
        self.assertEqual(trace, ["java -jar target/quest.jar solve 101-01 --yes", "build", "java -jar target/quest.jar exercise 101-01 both", "java -jar target/quest.jar verify 101-01"])

    def test_skip_solution_also_rebuilds(self):
        code, trace = self.run_quest("skip", "101-01", FAIL_COMMAND="skip", FAIL_CODE="5")
        self.assertEqual(code, 0)
        self.assertIn("java -jar target/quest.jar verify 101-01", trace)

    def test_failed_exercise_does_not_verify(self):
        code, trace = self.run_quest("solve", "101-01", FAIL_COMMAND="solve", FAIL_CODE="5", EXERCISE_CODE="2")
        self.assertEqual(code, 2)
        self.assertFalse(any(" verify " in call for call in trace))

    def test_build_failure_does_not_execute_stale_jar(self):
        code, trace = self.run_quest("list", QUEST_REBUILD="1", BUILD_CODE="7")
        self.assertEqual(code, 7)
        self.assertEqual(trace, ["build"])

    def test_propagates_partial_verification(self):
        code, trace = self.run_quest("verify", "301-05", FAIL_COMMAND="verify", FAIL_CODE="3")
        self.assertEqual(code, 3)
        self.assertEqual(len(trace), 1)


if __name__ == "__main__":
    unittest.main()
