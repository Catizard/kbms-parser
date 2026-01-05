package io.github.catizard.kbms.parser.osu;

import java.util.Vector;

public class Colours {
    public static class RGB {
        public Integer red = 0;
        public Integer green = 0;
        public Integer blue = 0;
    }
    public Vector<RGB> combo = new Vector<RGB>();
    public RGB sliderTrackOverride;
    public RGB sliderBorder;
}
