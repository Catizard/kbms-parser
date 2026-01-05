package io.github.catizard.kbms.parser.osu;

import java.util.Vector;

public class HitObjects {
    public static class HitSample {
        public Integer normalSet = 0;
        public Integer additionalSet = 0;
        public Integer index = 0;
        public Integer volume = 0;
        public String filename = "";
    }
    public Integer x;
    public Integer y;
    public Integer time;
    public Integer type;
    public Integer hitSound;
    public Vector<String> objectParams = new Vector<String>();
    public HitSample hitSample = new HitSample();
}
