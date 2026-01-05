package io.github.catizard.kbms.parser.osu;

import bms.model.*;
import io.github.catizard.kbms.parser.ChartParser;
import io.github.catizard.kbms.parser.ChartParserConfig;
import io.github.catizard.kbms.parser.bms.BMSParseContext;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Vector;

import static io.github.catizard.kbms.parser.RadixHelperKt.convertHexString;

/**
 * This parser is copied from lr2oraja-endless dream's forked jbms-parser, original author is [MatVeiQaaa](https://github.com/MatVeiQaaa)
 *
 * @author MatVeiQaaa
 */
public class OSUParser extends ChartParser {

	private final LongNoteDef lnType;

	public OSUParser(ChartParserConfig config) {
		super(config);
		this.lnType = config.getLnType();
	}

	@NotNull
	@Override
	public BMSModel parse(@NotNull ChartInformation chartInformation) {
		Path f = chartInformation.getPath();
		BMSParseContext ctx = new BMSParseContext(getConfig(), null);
		MessageDigest md5digest, sha256digest;
		try {
			md5digest = MessageDigest.getInstance("MD5");
			sha256digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
			return null;
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new InputStreamReader(
					new DigestInputStream(new DigestInputStream(new ByteArrayInputStream(Files.readAllBytes(f)), md5digest), sha256digest),
					"MS932"));

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		Osu osu = new Osu(br);
		if (osu.timingPoints.isEmpty() || osu.hitObjects.isEmpty()) return null;
		String md5 = convertHexString(md5digest.digest());
		String sha256 = convertHexString(sha256digest.digest());
		if (osu.general.mode != 3) return null;

		int keymode = osu.difficulty.circleSize.intValue();
		ctx.setTitle(osu.metadata.title);
		ctx.setSubTitle("[" + osu.metadata.version + "]");
		ctx.setArtist(osu.metadata.artist);
		ctx.setSubArtist(osu.metadata.creator);
		ctx.setGenre(keymode + "K");
		ctx.setJudgeRank(3);
		ctx.setJudgeRankType(JudgeRankType.BMS_RANK);
		ctx.setTotal(0);
		ctx.setTotalType(TotalType.BMS);
		ctx.setPlayLevel("");
		int[] mapping;
		switch (keymode) {
			case 4: {
				ctx.setPlayMode(Mode.BEAT_7K);
				mapping = new int[]{0, 2, 4, 6, -1, -1, -1, -1};
				break;
			}
			case 5: {
				ctx.setPlayMode(Mode.BEAT_5K);
				mapping = new int[]{0, 1, 2, 3, 4, -1};
				break;
			}
			case 6: {
				ctx.setPlayMode(Mode.BEAT_7K);
				mapping = new int[]{0, 1, 2, 4, 5, 6, -1, -1};
				break;
			}
			case 7: {
				ctx.setPlayMode(Mode.BEAT_7K);
				mapping = new int[]{0, 1, 2, 3, 4, 5, 6, -1};
				break;
			}
			case 8: {
				ctx.setPlayMode(Mode.BEAT_7K);
				mapping = new int[]{7, 0, 1, 2, 3, 4, 5, 6};
				break;
			}
			case 9: {
				ctx.setPlayMode(Mode.POPN_9K);
				mapping = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
				break;
			}
			case 10: {
				ctx.setPlayMode(Mode.BEAT_10K);
				mapping = new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9, 10, -1, -1};
				break;
			}
			case 12: {
				ctx.setPlayMode(Mode.BEAT_10K);
				mapping = new int[]{5, 0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11};
				break;
			}
			case 14: {
				ctx.setPlayMode(Mode.BEAT_14K);
				mapping = new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, -1, -1};
				break;
			}
			case 16: {
				ctx.setPlayMode(Mode.BEAT_14K);
				mapping = new int[]{7, 0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15};
				break;
			}
			default:
				return null;
		}
		//model.setLnmode(BMSModel.LNTYPE_LONGNOTE);
		ctx.setBanner("");

		int offset = 38;
		ArrayList<String> bgaList = new ArrayList<String>();
		ArrayList<Events> videos = new ArrayList<Events>();
		ArrayList<Events> bgSounds = new ArrayList<Events>();
		Vector<String> wavmap = new Vector<String>();
		wavmap.add(osu.general.audioFilename);
		for (int i = 0; i < osu.events.size(); i++) {
			try {
				Events event = osu.events.get(i);
				switch(event.eventType) {
					case "0": {
						ctx.setBackBMP(event.eventParams.get(0));
						ctx.setStageFile(event.eventParams.get(0));
						break;
					}
					case "1":
					case "Video": {
						event.startTime += offset;
						String name = event.eventParams.get(0).replace("\"", "");
						bgaList.add(name);
						videos.add(event);
						break;
					}
					case "5":
					case "Sample": {
						String name = event.eventParams.get(1).replace("\"", "");
						wavmap.add(name);
						bgSounds.add(event);
						break;
					}
					default: continue;
				}
			} catch (NumberFormatException e) {
				continue;
			}
		}
		ctx.setPreview(osu.general.audioFilename);

		TreeMap<Integer, Timeline> timelines = new TreeMap<>();
		ArrayList<TimingPoints> timingPoints = new ArrayList<TimingPoints>();
		ArrayList<TimingPoints> svs = new ArrayList<TimingPoints>();
		for (int i = 0; i < osu.timingPoints.size(); i++) {
			TimingPoints point = osu.timingPoints.get(i);
			point.time += offset;
			if (point.uninherited) {
				timingPoints.add(point);

				TimingPoints sv = new TimingPoints();
				sv.time = point.time;
				sv.beatLength = -100.f;
				sv.meter = point.meter;
				sv.sampleSet = point.sampleSet;
				sv.sampleIndex = point.sampleIndex;
				sv.volume = point.volume;
				sv.uninherited = false;
				sv.effects = point.effects;
				if (i != osu.timingPoints.size() - 1) {
					if (!osu.timingPoints.get(i+1).time.equals(point.time)) {
						svs.add(sv);
					}
				}
				else {
					svs.add(sv);
				}
			} else {
				if (!svs.isEmpty()) {
					TimingPoints lastSv = svs.get(svs.size() - 1);
					if (lastSv.time.equals(point.time)) {
						lastSv.beatLength = point.beatLength;
						continue;
					}
				}
				svs.add(point);
			}
		}
		ctx.setBpm(GetBpm(timingPoints, 0));

		bgaList.add(ctx.getBackBMP());
		Timeline bgmTl = GetTimeline(ctx, timelines, 0, 0);
		Note bgm = new NormalNote(0, 0, 0);
		bgmTl.addBackgroundNote(bgm);
		bgmTl.setBpm(GetBpm(timingPoints, bgmTl.getTime()));
		bgmTl.setScroll(GetSv(svs, bgmTl.getTime()));
		bgmTl.setBgaID(bgaList.size() - 1);

		for (int i = 0; i < videos.size(); i++) {
			Events event = videos.get(i);
			Timeline timeline = GetTimeline(ctx, timelines, event.startTime, GetSection(timingPoints, event.startTime));
			timeline.setBgaID(i);
			timeline.setBpm(GetBpm(timingPoints, event.startTime));
			timeline.setScroll(GetSv(svs, event.startTime));
		}
		for (int i = 0; i < bgSounds.size(); i++) {
			Events event = bgSounds.get(i);
			Timeline timeline = GetTimeline(ctx, timelines, event.startTime, GetSection(timingPoints, event.startTime));
			Note note = new NormalNote(i+1, event.startTime, 0);
			timeline.addBackgroundNote(note);
			timeline.setBpm(GetBpm(timingPoints, event.startTime));
			timeline.setScroll(GetSv(svs, event.startTime));
		}
		for (TimingPoints point : timingPoints) {
			Timeline timeline = GetTimeline(ctx, timelines, point.time.intValue(), GetSection(timingPoints, point.time.intValue()));
			timeline.setBpm(1 / point.beatLength * 1000 * 60);
			timeline.setScroll(GetSv(svs, point.time.intValue()));
		}
		for (TimingPoints sv : svs) {
			Timeline timeline = GetTimeline(ctx, timelines, sv.time.intValue(), GetSection(timingPoints, sv.time.intValue()));
			timeline.setScroll(100.d / (-sv.beatLength.doubleValue()));
			timeline.setBpm(GetBpm(timingPoints, sv.time.intValue()));
		}

		for (int i = 0; i < timingPoints.size(); i++) {
			int lastNoteTime = osu.hitObjects.get(osu.hitObjects.size() - 1).time;
			TimingPoints point = timingPoints.get(i);
			int beginTime = point.time.intValue();
			int endTime = i < timingPoints.size() - 1 ? timingPoints.get(i + 1).time.intValue() : lastNoteTime;
			double beginSection = GetSection(timingPoints, beginTime);
			int duration = endTime - beginTime;
			float totalSections = duration / (point.beatLength * 4);
			if (totalSections > 10000) {
				Timeline firstLine = GetTimeline(ctx, timelines, beginTime, beginSection);
				Timeline lastLine = GetTimeline(ctx, timelines, endTime, beginSection + totalSections);
				firstLine.setBpm(1 / point.beatLength * 1000 * 60);
				lastLine.setBpm(firstLine.getBpm());
				firstLine.setScroll(GetSv(svs, beginTime));
				lastLine.setScroll(GetSv(svs, endTime));
				firstLine.setHasSectionLine(true);
				lastLine.setHasSectionLine(true);
				continue;
			}
			for (int section = 0; section <= (int)totalSections; section++) {
				int time = beginTime + (int)(section * point.beatLength * 4);
				Timeline line = GetTimeline(ctx, timelines, time, beginSection + section);
				line.setBpm(1 / point.beatLength * 1000 * 60);
				line.setScroll(GetSv(svs, time));
				line.setHasSectionLine(true);
			}
		}

		for (int i = 0; i < osu.hitObjects.size(); i++) {
			HitObjects hitObject = osu.hitObjects.get(i);
			if (hitObject.time < 0) continue;
			hitObject.time += offset;

			int columnIdx = ((int) Math.floor(hitObject.x * keymode / 512.f));
			columnIdx = Math.max(0, Math.min(keymode - 1, columnIdx));
			double section = GetSection(timingPoints, hitObject.time);

			Timeline timeline = GetTimeline(ctx, timelines, hitObject.time, section);
			timeline.setBpm(GetBpm(timingPoints, timeline.getTime()));
			timeline.setScroll(GetSv(svs, timeline.getTime()));
			Boolean isManiaHold = (hitObject.type & 0x80) > 0;
			int wavIdx = -2;
		/*if (!hitObject.hitSample.filename.isEmpty()) { // keysounds potentially go here.
			wavIdx = wavmap.size();
			wavmap.add(hitObject.hitSample.filename);
		}*/
			if (isManiaHold) {
				int tailTimeMs = Integer.parseInt(hitObject.objectParams.get(0)) + offset;
				long tailTimeUs = (long) tailTimeMs * 1000;
				if (tailTimeMs <= hitObject.time) {
					NormalNote note = new NormalNote(wavIdx, hitObject.time * 1000, 0);
					timeline.setNote(mapping[columnIdx], note);
					continue;
				}
				LongNote head = new LongNote(wavIdx, hitObject.time * 1000, 0, LongNoteDef.UNDEFINED);
				head.setType(ctx.getLnType());
				timeline.setNote(mapping[columnIdx], head);

				double tailSection = GetSection(timingPoints, tailTimeMs);
				LongNote tail = new LongNote(wavIdx, tailTimeUs, 0, LongNoteDef.UNDEFINED);
				tail.setType(ctx.getLnType());
				Timeline tailTl = GetTimeline(ctx, timelines, tailTimeMs, tailSection);
				tailTl.setBpm(GetBpm(timingPoints, tailTimeMs));
				tailTl.setScroll(GetSv(svs, tailTimeMs));
				tailTl.setNote(mapping[columnIdx], tail);

				head.connectPair(tail);
			} else {
				NormalNote note = new NormalNote(wavIdx, hitObject.time * 1000, 0);
				timeline.setNote(mapping[columnIdx], note);
			}
		}

		return new BMSModel(ctx, new ChartInformation(f, ctx.getLnType(), ctx.getSelectedRandoms()), md5, sha256);
	}

	TimingPoints GetTimingPoint(ArrayList<TimingPoints> timingPoints, long time) {
		TimingPoints entry = timingPoints.get(0);
		int lastIdx = 0;
		while(entry.time < time) {
			try {
				TimingPoints nextEntry = timingPoints.get(++lastIdx);
				while (nextEntry.time <= entry.time) nextEntry = timingPoints.get(++lastIdx);
				if (nextEntry.time <= time) entry = nextEntry;
				else break;
			}
			catch (IndexOutOfBoundsException e) {
				break;
			}
		}
		return entry;
	}

	double GetBpm(ArrayList<TimingPoints> timingPoints, int time) {
		TimingPoints point = GetTimingPoint(timingPoints, time);
		return 1 / point.beatLength * 1000 * 60;
	}

	double GetSv(ArrayList<TimingPoints> svs, int time) {
		TimingPoints entry;
		try {
			entry = svs.get(0);
		}
		catch (IndexOutOfBoundsException e) {
			return 1;
		}
		if (entry.time.intValue() > time) return 1;
		int lastIdx = 0;
		while(entry.time.intValue() < time) {
			try {
				TimingPoints nextEntry = svs.get(++lastIdx);
				while (nextEntry.time <= entry.time) nextEntry = svs.get(++lastIdx);
				if (nextEntry.time.intValue() <= time) entry = nextEntry;
				else break;
			}
			catch (IndexOutOfBoundsException e) {
				break;
			}
		}
		return 100.d / (-entry.beatLength.doubleValue());
	}

	Timeline GetTimeline(BMSParseContext ctx, TreeMap<Integer, Timeline> timelines, int time, double section) {
		Timeline timeline = timelines.get(time);
		if (timeline == null) {
			timeline = new Timeline(section, (long)time * 1000, ctx.getPlayMode().key, ctx.getBpm(), 1.0);
			timelines.put(time, timeline);
		}
		return timeline;
	}

	double GetSection(ArrayList<TimingPoints> timingPoints, int time) {
		TimingPoints entry = timingPoints.get(0);
		double section;
		if (time <= entry.time) {
			section = time / (entry.beatLength * 4);
			return section;
		}
		section = entry.time / (entry.beatLength * 4);
		int lastIdx = 0;
		while(entry.time < time) {
			try {
				TimingPoints nextEntry = timingPoints.get(++lastIdx);
				while (nextEntry.time <= entry.time) nextEntry = timingPoints.get(++lastIdx);
				if (nextEntry.time > time) {
					section += (time - entry.time) / (entry.beatLength * 4);
					break;
				}
				section += (nextEntry.time - entry.time) / (entry.beatLength * 4);
				entry = nextEntry;
			}
			catch (IndexOutOfBoundsException e) {
				section += (time - entry.time) / (entry.beatLength * 4);
				break;
			}

		}
		return section;
	}
}
