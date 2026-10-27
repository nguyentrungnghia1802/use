"""Negative and compatibility contracts for the explicit final Auction gate.

Run: python -B -m unittest discover -s use-plugin/tools/tests -v
Fixtures qualify the validator only; they are never live Auction evidence.
"""
import copy
import base64
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("semantic_acceptance", Path(__file__).resolve().parents[1] / "semantic-acceptance.py")
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


class AcceptanceTest(unittest.TestCase):
    def setUp(self):
        owned = gate.ROOT / "use-plugin/target/acceptance-script-tests"
        owned.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=owned)
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def write(self, name, value):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(value), encoding="utf-8")
        return p

    def generic_program(self):
        def asset(ident, fmt, content):
            data = content.encode()
            return dict(id=ident, format=fmt, sha256=gate.sha(data), contentBase64=base64.b64encode(data).decode())
        return dict(schemaVersion="1.2.0", programHash="a" * 64, declarative=dict(version="JACAMO_MAPPING_XML/2.0",
                    xml=asset("xml", "JACAMO_MAPPING_XML/2.0", "<mapping/>"),
                    ocl=asset("ocl", "NATIVE_USE_OCL", "context Stock inv: true"), sources=[asset("jcm", "JCM", "mas sample {}")]))

    def test_generic_inputs_reject_oracles_legacy_fallback_and_digest_tamper(self):
        value = self.generic_program()
        gate.validate_declarative_inputs(value)
        value["case"] = {}
        with self.assertRaisesRegex(ValueError, "NO_LEGACY_FALLBACK"):
            gate.validate_declarative_inputs(value)
        del value["case"]
        value["declarative"]["sources"][0]["format"] = "CSL"
        with self.assertRaisesRegex(ValueError, "ORACLE_AS_PRODUCTION_SOURCE"):
            gate.validate_declarative_inputs(value)
        value["declarative"]["sources"][0]["format"] = "JCM"
        value["declarative"]["ocl"]["contentBase64"] = base64.b64encode(b"changed").decode()
        with self.assertRaisesRegex(ValueError, "GENERIC_INPUT_DIGEST"):
            gate.validate_declarative_inputs(value)

    def test_generic_recording_dialect_is_explicit_and_cannot_replace_legacy(self):
        value = self.generic_program()
        data = json.dumps(value).encode()
        receipt = dict(programHash=value["programHash"], stateHash="b" * 64, resultHash="c" * 64)
        manifest = dict(schemaVersion="3.3.0", programHash=value["programHash"], finalStateHash=receipt["stateHash"],
                        finalResultHash=receipt["resultHash"], files={"program.json": gate.sha(data)})
        path = self.root / "recording.zip"
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("program.json", data)
            z.writestr("manifest.json", json.dumps(manifest))
        self.assertEqual(value, gate.recording_program(path, receipt, declarative=True))
        with self.assertRaisesRegex(ValueError, "AUCTION_RECORDING_SCHEMA"):
            gate.recording_program(path, receipt)
        with self.assertRaisesRegex(ValueError, "GENERIC_REQUIRES_COMPATIBILITY_ACCEPTANCE"):
            gate.validate_generic({}, path, path)

    def test_old_gate_keeps_all_a_to_v_and_counter_requirements(self):
        names = {name for group in gate.MATRIX.values() for name in group}
        names |= {"Use4GoalPackagedPluginIT", "EngineInstallationGuardTest", "DeclaredArtifactTypeProofTest"}
        log = self.root / "verify.log"
        log.write_text("failsafe: integration-test\n" + "\n".join(
            f"Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s -- in test.{name}"
            for name in sorted(names)) + "\nBUILD SUCCESS", encoding="utf-8")
        with patch.object(gate, "protected_inputs", return_value=[]):
            result = gate.validate(log, require_installed_live=True)
        self.assertEqual(set("ABCDEFGHIJKLMNOPQRSTUV"), set(result["matrix"]))
        self.assertNotIn("auction", result)
        log.write_text(log.read_text().replace("Skipped: 0", "Skipped: 1", 1))
        with self.assertRaisesRegex(ValueError, "NONPASS_REQUIRED_SUITE"):
            gate.validate(log)

    def test_failed_full_reactor_cannot_be_waived_by_auction_collection(self):
        log = self.root / "failed-full.log"
        log.write_text("failsafe: integration-test\nBUILD FAILURE\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "REACTOR_NOT_SUCCESSFUL"):
            gate.validate(log, require_installed_live=True, collect_missing=True)

    def test_missing_protected_only_collects_blocked_in_explicit_mode(self):
        value = {"protected": [{"path": str(self.root / "lost-report.md"), "sha256": "A" * 64}]}
        self.write("use-plugin/verification/semantic-migration/phase-0/baseline-inputs.json", value)
        with patch.object(gate, "ROOT", self.root):
            with self.assertRaises(FileNotFoundError):
                gate.protected_inputs()
            result = gate.protected_inputs(True)
            self.assertFalse(result[0]["unchanged"])
            self.assertEqual("BLOCKED", result[0]["status"])

    def test_auction_lost_exception_is_exact_and_keeps_unchanged_false(self):
        path = gate.ROOT / "use-plugin/verification/auction/Q08/acceptance-profile.json"
        policy = gate.auction_exceptions(path)
        protected = [dict(policy["exceptions"][0], unchanged=False, reason="PROTECTED_INPUT_MISSING")]
        gate.apply_auction_exceptions(protected, policy)
        self.assertFalse(protected[0]["unchanged"])
        self.assertEqual("LOST", protected[0]["status"])
        self.assertTrue(protected[0]["acceptedException"])
        modified = [dict(policy["exceptions"][0], unchanged=False, reason="PROTECTED_SOURCE_CHANGED")]
        with self.assertRaisesRegex(ValueError, "AUCTION_EXCEPTION_ONLY_MISSING"):
            gate.apply_auction_exceptions(modified, policy)
        policy["exceptions"][0]["sha256"] = "A" * 64
        with self.assertRaisesRegex(ValueError, "AUCTION_EXCEPTION_NOT_AUTHORIZED"):
            gate.auction_exceptions(self.write("wrong-policy.json", policy))
        with self.assertRaisesRegex(ValueError, "AUCTION_EXCEPTION_BASELINE_MISMATCH"):
            gate.apply_auction_exceptions(protected, policy)

    def test_exception_policy_cannot_cover_another_missing_file(self):
        policy = gate.auction_exceptions(gate.ROOT / "use-plugin/verification/auction/Q08/acceptance-profile.json")
        protected = [dict(policy["exceptions"][0], unchanged=False, reason="PROTECTED_INPUT_MISSING"),
                     dict(path="another-file", sha256="B" * 64, unchanged=False, status="BLOCKED", reason="PROTECTED_INPUT_MISSING")]
        gate.apply_auction_exceptions(protected, policy)
        self.assertNotIn("acceptedException", protected[1])
        self.assertEqual("BLOCKED", protected[1]["status"])

    def test_modified_protected_is_never_waived(self):
        p = self.write("protected.json", {"changed": True})
        self.write("use-plugin/verification/semantic-migration/phase-0/baseline-inputs.json",
                   {"protected": [{"path": str(p), "sha256": "A" * 64}]})
        with patch.object(gate, "ROOT", self.root):
            with self.assertRaisesRegex(ValueError, "PROTECTED_SOURCE_CHANGED"):
                gate.protected_inputs(True)

    def recording(self, version="3.1.0", budget=None, corrupt=False):
        program = {"schemaVersion": "1.1.0", "programHash": "program"}
        data = json.dumps(program).encode()
        manifest = {"schemaVersion": version, "files": {"program.json": gate.sha(data)},
                    "programHash": "program", "finalStateHash": "state", "finalResultHash": "result"}
        if budget is not None:
            manifest["runtimeByteLimit"] = budget
        p = self.root / "recording.zip"
        with zipfile.ZipFile(p, "w") as z:
            z.writestr("manifest.json", json.dumps(manifest))
            z.writestr("program.json", data + (b" " if corrupt else b""))
        return p, {"programHash": "program", "stateHash": "state", "resultHash": "result"}

    def test_old_recording_and_budgeted_recording_are_distinct(self):
        p, receipt = self.recording()
        self.assertEqual("program", gate.recording_program(p, receipt)["programHash"])
        p, receipt = self.recording("3.2.0", 384 * 1024**2)
        self.assertEqual("program", gate.recording_program(p, receipt)["programHash"])

    def test_budget_missing_invalid_or_downgraded_is_rejected(self):
        for version, budget in [("3.2.0", None), ("3.2.0", 256 * 1024**2),
                                ("3.2.0", 1024**3 + 1), ("3.2.0", "384"),
                                ("3.2.0", 402653184.5), ("3.1.0", 384 * 1024**2)]:
            with self.subTest(version=version, budget=budget):
                p, receipt = self.recording(version, budget)
                with self.assertRaisesRegex(ValueError, "AUCTION_REPLAY_BUDGET"):
                    gate.recording_program(p, receipt)

    def test_recording_hash_and_replay_result_tamper_are_rejected(self):
        p, receipt = self.recording(corrupt=True)
        with self.assertRaisesRegex(ValueError, "AUCTION_RECORDING_HASH"):
            gate.recording_program(p, receipt)
        p, receipt = self.recording()
        receipt["stateHash"] = "other"
        with self.assertRaisesRegex(ValueError, "AUCTION_RECORDING_FINAL"):
            gate.recording_program(p, receipt)

    def test_house_fixture_witness_cannot_be_replaced_by_old_original_source_claim(self):
        log = self.root / "house.log"
        log.write_text("HOUSE_BUILDING_DOMAIN_LIVE_PASS evidence=" + str(self.root), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "HOUSE_FIXTURE_LIVE_WITNESS"):
            gate.validate_house_fixture(log)

    def test_outside_evidence_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "AUCTION_EVIDENCE_OUTSIDE_USE"):
            gate.repository_path(str(gate.ROOT.parent / "use4Goal"))

    def test_canonical_commitment_roster_differs_from_explicit_controlled_roster(self):
        r = {"requiredPauseAgents": ["bob", "alice", "maria", "francois", "giacomo"],
             "players": ["bob", "alice", "maria", "francois"]}
        gate.validate_task_roster("canonical", r)
        with self.assertRaisesRegex(ValueError, "AUCTION_CONTROLLED_TASK_ROSTER"):
            gate.validate_task_roster("controlled", r)
        r["players"] = list(r["requiredPauseAgents"])
        gate.validate_task_roster("controlled", r)
        for players in (["bob"], ["bob", "foreign-player"], ["bob", "alice", "alice"]):
            r["players"] = players
            with self.assertRaisesRegex(ValueError, "AUCTION_TASK_ROSTER"):
                gate.validate_task_roster("canonical", r)

    def test_live_receipt_cannot_substitute_scope_pid_or_actual_witnesses(self):
        source = gate.ROOT / "use-plugin/verification/auction/E02/installed-live"
        # Captured real receipt is only the seed for negative validator tests.
        run = gate.read_json(source / "run.json")
        original = gate.read_json(source / "consumer-receipt.json")
        self.write("run.json", run)
        cases = [("scope", "INSTALLED_COUNTER", "AUCTION_LIVE_SCOPE"),
                 ("producerPid", original["consumerPid"], "AUCTION_PID_PAIR"),
                 ("journalGap", True, "AUCTION_LIVE_GAP"),
                 ("originalCalls", [], "AUCTION_ORIGINAL_CORE_CALLS"),
                 ("heartbeatP95Millis", 250, "AUCTION_EDT_BOUND"),
                 ("engineClasses", {}, "AUCTION_ENGINE_OWNER")]
        for field, value, diagnostic in cases:
            with self.subTest(field=field):
                r = copy.deepcopy(original)
                r[field] = value
                self.write("consumer-receipt.json", r)
                with self.assertRaisesRegex(ValueError, diagnostic):
                    gate.validate_auction_run("controlled", self.root, run["fullHostArchiveSha256"], {}, {})


if __name__ == "__main__":
    unittest.main()
