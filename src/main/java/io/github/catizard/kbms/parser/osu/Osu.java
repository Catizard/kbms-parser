package io.github.catizard.kbms.parser.osu;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

public class Osu {
    public Osu(BufferedReader br) {
        try
        {
            String line = null;
            String section = "";
            while ((line = br.readLine()) != null) {
                line = line.split("//")[0];
                if (line.length() < 2) {
                    continue;
                }
                String lineNoSpace = line.replaceAll("\\s","");
                if (lineNoSpace.charAt(0) == '[') {
                    section = lineNoSpace.substring(1, lineNoSpace.length() - 1);
                    continue;
                }
                Integer delimiter = line.indexOf(':');

                String key;
                String value;
                if (delimiter > 0) {
                    if (delimiter > 1 && line.charAt(delimiter - 1) == ' ') {
                        key = line.substring(0, delimiter - 1);
                    }
                    else {
                        key = line.substring(0, delimiter);
                    }
                }
                else {
                    key = "";
                }
                if (line.length() <= delimiter + 1) {
                    value = "";
                }
                else if (line.charAt(delimiter + 1) == ' ') {
                    value = line.substring(delimiter + 2);
                }
                else {
                    value = line.substring(delimiter + 1);
                }
                switch (section) {
                    case "General": {
                        switch(key) {
                            case "AudioFilename": {
                                this.general.audioFilename = value;
                                break;
                            }
                            case "AudioLeadIn": {
                                try {
                                    this.general.audioLeadIn = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "AudioHash": {
                                this.general.audioHash = value;
                                break;
                            }
                            case "PreviewTime": {
                                try {
                                    this.general.previewTime = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "Countdown": {
                                try {
                                    this.general.countdown = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "SampleSet": {
                                this.general.sampleSet = value;
                                break;
                            }
                            case "StackLeniency": {
                                try {
                                    this.general.stackLeniency = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "Mode": {
                                try {
                                    this.general.mode = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "LetterboxInBreaks": {
                                try {
                                    this.general.letterboxInBreaks = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                            }
                            case "StoryFireInFront": {
                                try {
                                    this.general.storyFireInFront = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "UseSkinSprites": {
                                try {
                                    this.general.useSkinSprites = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "AlwaysShowPlayfield": {
                                try {
                                    this.general.alwaysShowPlayfield = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "OverlayPosition": {
                                this.general.overlayPosition = value;
                                break;
                            }
                            case "SkinPreference": {
                                this.general.skinPreference = value;
                                break;
                            }
                            case "EpilepsyWarning": {
                                try {
                                    this.general.epilepsyWarning = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "CountdownOffset": {
                                try {
                                    this.general.countdownOffset = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "SpecialStyle": {
                                try {
                                    this.general.specialStyle = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "WidescreenStoryboard": {
                                try {
                                    this.general.widescreenStoryboard = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "SamplesMatchPlaybackRate": {
                                try {
                                    this.general.samplesMatchPlaybackRate = (Integer.parseInt(value) >= 1);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                        }
                    }
                    case "Editor": {
                        switch(key) {
                            case "Bookmarks": {
                                String[] values = value.split(",");
                                this.editor.bookmarks = new Integer[values.length];
                                for (int i = 0; i < values.length; i++) {
                                    try {
                                        this.editor.bookmarks[i] = Integer.parseInt(values[i]);
                                    }
                                    catch (NumberFormatException e) {
                                        continue;
                                    }
                                }
                                break;
                            }
                            case "DistanceSpacing": {
                                try {
                                    this.editor.distanceSpacing = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "BeatDivisor": {
                                try {
                                    this.editor.beatDivisor = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "GridSize": {
                                try {
                                    this.editor.gridSize = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "TimelineZoom": {
                                try {
                                    this.editor.timelineZoom = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case "Metadata": {
                        switch(key) {
                            case "Title": {
                                this.metadata.title = value;
                                break;
                            }
                            case "TitleUnicode": {
                                this.metadata.titleUnicode = value;
                                break;
                            }
                            case "Artist": {
                                this.metadata.artist = value;
                                break;
                            }
                            case "ArtistUnicode": {
                                this.metadata.artistUnicode = value;
                                break;
                            }
                            case "Creator": {
                                this.metadata.creator = value;
                                break;
                            }
                            case "Version": {
                                this.metadata.version = value;
                                break;
                            }
                            case "Source": {
                                this.metadata.source = value;
                                break;
                            }
                            case "Tags": {
                                this.metadata.tags = value.split(" ");
                                break;
                            }
                            case "BeatmapID": {
                                try {
                                    this.metadata.beatmapId = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "BeatmapSetID": {
                                try {
                                    this.metadata.beatmapSetId = Integer.parseInt(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case "Difficulty": {
                        switch(key) {
                            case "HPDrainRate": {
                                try {
                                    this.difficulty.hpDrainRate = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "CircleSize": {
                                try {
                                    this.difficulty.circleSize = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "OverallDifficulty": {
                                try {
                                    this.difficulty.overallDifficulty = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "ApproachRate": {
                                try {
                                    this.difficulty.approachRate = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "SliderMultiplier": {
                                try {
                                    this.difficulty.sliderMultiplier = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                            case "SliderTickRate": {
                                try {
                                    this.difficulty.sliderTickRate = Float.parseFloat(value);
                                }
                                catch (NumberFormatException e) {
                                    continue;
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case "Events": {
                        String[] values = value.split(",");
                        if (values.length < 3) continue;
                        Events event = new Events();
                        event.eventType = values[0];
                        try {
                            event.startTime = Integer.parseInt(values[1]);
                        }
                        catch(NumberFormatException e) {
                            event.startTime = 0;
                        }
                        for (int i = 2; i < values.length; i++) {
                            event.eventParams.add(values[i].replace("\"", ""));
                        }
                        this.events.add(event);
                        break;
                    }
                    case "TimingPoints": {
                        String[] values = value.split(",");
                        if (values.length < 6) continue;
                        TimingPoints timingPoint = new TimingPoints();
                        try {
                            timingPoint.time = Float.parseFloat(values[0]);
                            timingPoint.beatLength = Float.parseFloat(values[1]);
                            timingPoint.meter = Integer.parseInt(values[2]);
                            timingPoint.sampleSet = Integer.parseInt(values[3]);
                            timingPoint.sampleIndex = Integer.parseInt(values[4]);
                            timingPoint.volume = Integer.parseInt(values[5]);
                        }
                        catch(NumberFormatException e) {
                            continue;
                        }
                        try {
                            timingPoint.uninherited = (Integer.parseInt(values[6]) >= 1);
                        }
                        catch (NumberFormatException e) {
                            timingPoint.uninherited = true;
                        }
                        try {
                            timingPoint.effects = Integer.parseInt(values[7]);
                        }
                        catch (NumberFormatException e) {
                            timingPoint.effects = 0;
                        }
                        this.timingPoints.add(timingPoint);
                        break;
                    }
                    case "Colours": {
                        String[] values = value.split(",");
                        if (values.length < 3) continue;
                        Colours.RGB rgb = new Colours.RGB();
                        try {
                            rgb.red = Integer.parseInt(values[0]);
                            rgb.green = Integer.parseInt(values[1]);
                            rgb.blue = Integer.parseInt(values[2]);
                        }
                        catch(NumberFormatException e) {
                            continue;
                        }
                        switch(key) {
                            case "SliderTrackOverride": {
                                this.colours.sliderTrackOverride = rgb;
                                break;
                            }
                            case "SliderBorder": {
                                this.colours.sliderBorder = rgb;
                                break;
                            }
                            default: {
                                if (key.startsWith("Combo")) {
                                    this.colours.combo.add(rgb);
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case "HitObjects": {
                        if (!key.isEmpty() && key.charAt(0) != ' ') {
                            key += ':';
                            value = key.concat(value);
                        }

                        String[] values = value.split(",");
                        if (values.length < 6) continue;
                        HitObjects hitObject = new HitObjects();
                        try {
                            hitObject.x = Integer.parseInt(values[0]);
                            hitObject.y = Integer.parseInt(values[1]);
                            hitObject.time = Integer.parseInt(values[2]);
                            hitObject.type = Integer.parseInt(values[3]);
                            hitObject.hitSound = Integer.parseInt(values[4]);
                        }
                        catch (NumberFormatException e) {
                            continue;
                        }
                        Boolean isManiaHold = (hitObject.type & 0x80) > 0;
                        if (isManiaHold) {
                            String[] hitSampleValues = values[values.length -1].split(":");
                            hitObject.objectParams.add(hitSampleValues[0]);
                            if (hitSampleValues.length < 5) continue;
                            try {
                                hitObject.hitSample.normalSet = Integer.parseInt(hitSampleValues[1]);
                                hitObject.hitSample.additionalSet = Integer.parseInt(hitSampleValues[2]);
                                hitObject.hitSample.index = Integer.parseInt(hitSampleValues[3]);
                                hitObject.hitSample.volume = Integer.parseInt(hitSampleValues[4]);
                            }
                            catch (NumberFormatException e) {
                                continue;
                            }
                            if (values[values.length - 1].endsWith(":")) {
                                hitObject.hitSample.filename = "";
                            }
                            else {
                                hitObject.hitSample.filename = hitSampleValues[5];
                            }
                        }
                        else {
                            String[] hitSampleValues = values[values.length -1].split(":");
                            if (hitSampleValues.length < 4) continue;
                            try {
                                hitObject.hitSample.normalSet = Integer.parseInt(hitSampleValues[0]);
                                hitObject.hitSample.additionalSet = Integer.parseInt(hitSampleValues[1]);
                                hitObject.hitSample.index = Integer.parseInt(hitSampleValues[2]);
                                hitObject.hitSample.volume = Integer.parseInt(hitSampleValues[3]);
                            }
                            catch (NumberFormatException e) {
                                continue;
                            }
                            if (values[values.length - 1].endsWith(":")) {
                                hitObject.hitSample.filename = "";
                            }
                            else {
                                hitObject.hitSample.filename = hitSampleValues[4];
                            }
                        }
                        this.hitObjects.add(hitObject);
                        break;
                    }
                    default: {
                        continue;
                    }
                }
            }
        }
        catch (IOException e) {
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
    public General general = new General();
    public Editor editor = new Editor();
    public Metadata metadata = new Metadata();
    public Difficulty difficulty = new Difficulty();
    public Vector<Events> events = new Vector<Events>();
    public Vector<TimingPoints> timingPoints = new Vector<TimingPoints>();
    public Colours colours = new Colours();
    public Vector<HitObjects> hitObjects = new Vector<HitObjects>();
}
