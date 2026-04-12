#!/usr/bin/env python3
# This module demonstrates comment removal.
# It has several comment styles.

import os  # standard library import
import sys

# -------------------------
# Helper functions
# -------------------------

def add(a, b):
    # Adds two numbers.
    result = a + b  # compute sum
    return result


# Standalone top-level comment
x = add(1, 2)  # invoke add
print(x)  # display result
