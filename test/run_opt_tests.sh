#!/bin/bash
# Optimization verification tests
# Checks: 1) output correctness (Path 1 == Path 2)
#          2) instruction count actually decreases

set -e

BASEDIR="$(cd "$(dirname "$0")/.." && pwd)"
CP="src:lib/JFlex.jar:lib/cup_v10k.jar:lib/symboltable-1-1.jar:lib/mj-runtime-1.1.jar"
RTCP="lib/mj-runtime-1.1.jar"

PASS=0
FAIL=0

run_opt_test() {
    local name="$1"
    local src="$2"
    local input="$3"
    local min_removed="$4"  # minimum instructions that should be removed

    local obj="/tmp/mjopt_${name}.obj"
    local obj_ir="/tmp/mjopt_${name}_ir.obj"

    # Compile and capture optimization stats
    local compile_out
    compile_out=$(java -cp "$CP" rs.ac.bg.etf.pp1.Compiler "$src" "$obj" 2>&1)

    if ! echo "$compile_out" | grep -q "Path 2 (IR):"; then
        echo "FAIL $name: compilation failed"
        FAIL=$((FAIL + 1))
        return
    fi

    # Check correctness: Path 1 == Path 2
    local p1 p2
    if [ -n "$input" ]; then
        p1=$(echo "$input" | timeout 5 java -cp "$RTCP" rs.etf.pp1.mj.runtime.Run "$obj" 2>/dev/null | sed 's/Completion took .*//' | sed 's/[[:space:]]*$//')
        p2=$(echo "$input" | timeout 5 java -cp "$RTCP" rs.etf.pp1.mj.runtime.Run "$obj_ir" 2>/dev/null | sed 's/Completion took .*//' | sed 's/[[:space:]]*$//')
    else
        p1=$(timeout 5 java -cp "$RTCP" rs.etf.pp1.mj.runtime.Run "$obj" 2>/dev/null | sed 's/Completion took .*//' | sed 's/[[:space:]]*$//')
        p2=$(timeout 5 java -cp "$RTCP" rs.etf.pp1.mj.runtime.Run "$obj_ir" 2>/dev/null | sed 's/Completion took .*//' | sed 's/[[:space:]]*$//')
    fi

    if [ "$p1" != "$p2" ]; then
        echo "FAIL $name: output differs after optimization"
        echo "  Path 1: $(echo "$p1" | head -1)"
        echo "  Path 2: $(echo "$p2" | head -1)"
        FAIL=$((FAIL + 1))
        rm -f "$obj" "$obj_ir"
        return
    fi

    # Check optimization effectiveness
    local opt_line
    opt_line=$(echo "$compile_out" | grep "^IR optimization:" || echo "")

    if [ -z "$opt_line" ]; then
        echo "FAIL $name: no optimization occurred (expected at least $min_removed removed)"
        FAIL=$((FAIL + 1))
        rm -f "$obj" "$obj_ir"
        return
    fi

    local removed
    removed=$(echo "$opt_line" | grep -o '[0-9]* removed' | grep -o '[0-9]*')

    if [ "$removed" -ge "$min_removed" ]; then
        echo "PASS $name ($opt_line)"
        PASS=$((PASS + 1))
    else
        echo "FAIL $name: only $removed instructions removed (expected >= $min_removed)"
        FAIL=$((FAIL + 1))
    fi

    rm -f "$obj" "$obj_ir"
}

cd "$BASEDIR"

echo "=== Optimization Verification Tests ==="
echo ""

# Constant folding: 5+3+3+3 binary ops foldable, each removes 2 CONSTs + 1 BinOp -> ~18 removed
run_opt_test "constant_folding" "test/opt_constant_folding.mj" "" 10

# Dead code: unused variables and folded constants -> instructions removed
run_opt_test "dead_code" "test/opt_dead_code.mj" "" 5

# Constant propagation: variable assignments with known values propagated through uses
run_opt_test "const_propagation" "test/opt_const_propagation.mj" "" 8

# Mixed: constants in conditions, foreach, array sizing
run_opt_test "mixed" "test/opt_mixed.mj" "" 5

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
