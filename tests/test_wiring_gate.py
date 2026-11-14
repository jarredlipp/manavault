#!/usr/bin/env python3
"""Regression checks for DEX method-code scanner; never trust a method_id alone."""
import importlib.util
from pathlib import Path
import unittest

path=Path(__file__).resolve().parents[1]/'tools/verify_scanner_wiring.py'
spec=importlib.util.spec_from_file_location('wiring',path)
wiring=importlib.util.module_from_spec(spec)
spec.loader.exec_module(wiring)

class DexInstructions(unittest.TestCase):
    def test_true_invoke_detected(self):
        # invoke-virtual {v0}, method@0007, followed by a return-void.
        self.assertEqual([7],wiring.invoked_method_indices([0x106e,7,0,0x0e]))
    def test_operands_not_false_invoke(self):
        # const/16 v0, #0x006e, return-void. A naive scan would see 0x6e.
        self.assertEqual([],wiring.invoked_method_indices([0x0013,0x006e,0x000e]))
    def test_switch_payload_not_false_invoke(self):
        self.assertEqual([],wiring.invoked_method_indices([0x0100,0x0001,0,0,0x006e,0x000e]))
    def test_fill_array_payload_not_false_invoke(self):
        self.assertEqual([],wiring.invoked_method_indices([0x0300,2,1,0,0x006e,0x000e]))
    def test_invoke_range(self):
        self.assertEqual([75],wiring.invoked_method_indices([0x0174,75,1,0x000e]))
    def test_unknown_opcode_fails_closed(self):
        with self.assertRaises(ValueError):
            wiring.invoked_method_indices([0x00f8,0x000e])

if __name__=='__main__':unittest.main()
