#!/bin/bash
# E2E test runner for MicroJava compiler
# Compiles each test via both paths (direct + IR), runs both, compares output
# Exit code: 0 if all pass, 1 if any fail

set -e

BASEDIR="$(cd "$(dirname "$0")/.." && pwd)"
CP="src:lib/JFlex.jar:lib/cup_v10k.jar:lib/symboltable-1-1.jar:lib/mj-runtime-1.1.jar"
RTCP="lib/mj-runtime-1.1.jar"
TESTDIR="$BASEDIR/test"

PASS=0
FAIL=0
SKIP=0
TOTAL=0

get_output() {
    local obj="$1"
    local input="$2"
    if [ -n "$input" ]; then
        echo "$input" | timeout 5 java -cp "$RTCP" rs.etf.pp1.mj.runtime.Run "$obj" 2>/dev/null | sed 's/Completion took .*//' | sed 's/[[:space:]]*$//'
    else
        timeout 5 java -cp "$RTCP" rs.etf.pp1.mj.runtime.Run "$obj" 2>/dev/null | sed 's/Completion took .*//' | sed 's/[[:space:]]*$//'
    fi
}

# Run test with expected output check + Path 1 vs Path 2 comparison
run_test() {
    local name="$1"
    local input="$2"
    local expected="$3"

    TOTAL=$((TOTAL + 1))

    local src="$TESTDIR/${name}.mj"
    local obj="$TESTDIR/${name}.obj"
    local obj_ir="$TESTDIR/${name}_ir.obj"

    if ! java -cp "$CP" rs.ac.bg.etf.pp1.Compiler "$src" "$obj" > /dev/null 2>&1; then
        echo "FAIL $name: compilation failed"
        FAIL=$((FAIL + 1))
        return
    fi

    local actual1
    actual1=$(get_output "$obj" "$input")

    local expected_trimmed
    expected_trimmed=$(echo "$expected" | sed 's/[[:space:]]*$//')
    if [ "$actual1" != "$expected_trimmed" ]; then
        echo "FAIL $name (Path 1: wrong output)"
        echo "  Expected: $(echo "$expected_trimmed" | head -2)"
        echo "  Actual:   $(echo "$actual1" | head -2)"
        FAIL=$((FAIL + 1))
        rm -f "$obj" "$obj_ir"
        return
    fi

    if [ ! -f "$obj_ir" ]; then
        echo "FAIL $name (Path 2: _ir.obj not produced)"
        FAIL=$((FAIL + 1))
        rm -f "$obj"
        return
    fi

    local actual2
    actual2=$(get_output "$obj_ir" "$input")

    if [ "$actual1" = "$actual2" ]; then
        echo "PASS $name"
        PASS=$((PASS + 1))
    else
        echo "FAIL $name (Path 2 output differs from Path 1)"
        echo "  Path 1: $(echo "$actual1" | head -2)"
        echo "  Path 2: $(echo "$actual2" | head -2)"
        FAIL=$((FAIL + 1))
    fi

    rm -f "$obj" "$obj_ir"
}

# Run test comparing Path 1 vs Path 2 only (no expected output needed)
run_compare() {
    local name="$1"
    local src="$2"
    local input="$3"
    local known_fail="$4"  # "skip" to mark as known failure

    TOTAL=$((TOTAL + 1))

    local obj="/tmp/mjtest_${name}.obj"
    local obj_ir="/tmp/mjtest_${name}_ir.obj"

    if ! java -cp "$CP" rs.ac.bg.etf.pp1.Compiler "$src" "$obj" > /dev/null 2>&1; then
        echo "FAIL $name: compilation failed"
        FAIL=$((FAIL + 1))
        return
    fi

    if [ ! -f "$obj_ir" ]; then
        if [ "$known_fail" = "skip" ]; then
            echo "SKIP $name (Path 2: _ir.obj not produced — known gap)"
            SKIP=$((SKIP + 1))
            rm -f "$obj"
            return
        fi
        echo "FAIL $name (Path 2: _ir.obj not produced)"
        FAIL=$((FAIL + 1))
        rm -f "$obj"
        return
    fi

    local actual1
    actual1=$(get_output "$obj" "$input")
    local actual2
    actual2=$(get_output "$obj_ir" "$input")

    if [ "$actual1" = "$actual2" ]; then
        echo "PASS $name"
        PASS=$((PASS + 1))
    else
        if [ "$known_fail" = "skip" ]; then
            echo "SKIP $name (Path 2 differs — known gap)"
            SKIP=$((SKIP + 1))
        else
            echo "FAIL $name (Path 2 output differs from Path 1)"
            echo "  Path 1: $(echo "$actual1" | head -2)"
            echo "  Path 2: $(echo "$actual2" | head -2)"
            FAIL=$((FAIL + 1))
        fi
    fi

    rm -f "$obj" "$obj_ir"
}

cd "$BASEDIR"

echo "=== MicroJava E2E Tests ==="
echo ""
echo "--- New test suite (with expected output) ---"

run_test "test_arithmetic" "" "    5
    6
   21
    5
    2
    7
   14
   20"

run_test "test_variables" "" "   35
   34
   35
   33"

run_test "test_local_vars" "" "  120
   14"

run_test "test_if_else" "" "    1
    0
    1
    0
    1
    0"

run_test "test_nested_control" "" "    4
   15
    1"

run_test "test_while" "" "   10
    5
   25"

run_test "test_methods" "" "    7
   25
   25"

run_test "test_multi_return" "" "    5    3    0
    5    0   10
    1   -1    0"

run_test "test_arrays" "" "   10   20   30   40   50
  150
  151"

run_test "test_array_postfix" "" "   11   19   32"

run_test "test_multiple_assign" "" "   10   20   30
   10   30"

run_test "test_foreach_block" "" "   10
   24
    7"

run_test "test_bool_logic" "" "    1
    0
    1
    0
    1
    1"

run_test "test_bool_var" "" "    1
    0
    1
    1"

run_test "test_chars" "" "A   65
B
XYZ"

run_test "test_print_width" "" "42
   42
        42
   -7
  A"

echo ""
echo "--- Original tests (Path 1 vs Path 2 comparison) ---"

run_compare "nested_conditions" "test/nested_conditions.mj" ""
run_compare "test302"           "test/test302.mj"           "1 2 3"
run_compare "nested_calls"      "test/nested_calls.mj"      ""
run_compare "symbol_detection"  "test/symbol_detection.mj"  ""
run_compare "matrix"            "test/matrix.mj"            ""
run_compare "polymorphism"      "test/polymorphism.mj"      ""
run_compare "linked_list"       "test/linked_list.mj"       ""

run_compare "test_read"         "test/test_read.mj"         "3 7 10 20 30"
run_compare "test301"           "test/test301.mj"           "5 3"
run_compare "test303"           "test/test303.mj"           "1 2"

echo ""
echo "=== Results: $PASS passed, $FAIL failed, $SKIP skipped (of $TOTAL total) ==="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
