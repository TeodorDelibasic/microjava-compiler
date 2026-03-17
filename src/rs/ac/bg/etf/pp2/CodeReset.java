package rs.ac.bg.etf.pp2;

import rs.etf.pp1.mj.runtime.Code;

public class CodeReset {

    public static void reset() {
        Code.buf = new byte[8192];
        Code.pc = 0;
        Code.mainPc = 0;
        Code.greska = false;
    }
}
