package com.cherokeelessons.eim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.filechooser.FileSystemView;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;

import com.cherokeelessons.eim.ChallengeResponsePair.ResponseLayout;
import com.cherokeelessons.lyx.LyxTemplate;

public class App implements Runnable {
	private String inFolder;
	private String outFolder;
	private int depth = 5;
	private int maxsets = 0;
	private ResponseLayout layout = ResponseLayout.SingleLine;
	private boolean includeRerversed = false;
	private boolean onlyRerversed = false;
	private float maxSetSize = 15;
	private Map<String, ReplacementSet> randomReplacements = new HashMap<>();
	private boolean sort = true;
	private boolean random = false;
	private String sep = ":";

	private void setDefaults(){
		depth = 5;
		maxsets = 0;
		layout = ResponseLayout.SingleLine;
		includeRerversed = false;
		randomReplacements.clear();
		sort=true;
		random=false;
		sep=":";
		maxSetSize=15;
	}
	
	public App(String[] args) {
	}

	@Override
	public void run() {
		try {
			_run();
			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void _run() throws IOException {
		initFolders();

		List<ChallengeResponsePair> challenges;
		List<ChallengeResponsePair> queued;

		Collection<File> cfiles = FileUtils.listFiles(new File(inFolder), null, false);
		List<File> files = new ArrayList<>(cfiles);
		Collections.sort(files);
		for (File file : files) {
			System.out.println("File: "+file.getName());
			setDefaults();
			File outFile = new File(outFolder, file.getName());
			String lyxBaseFile = new File(outFolder, FilenameUtils.getBaseName(file.getName())).getAbsolutePath();
			challenges = parseChallengeResponsePairs(file);
			System.out.println("\tLoaded "+challenges.size()+" challenges.");
			if (includeRerversed||onlyRerversed){
				List<ChallengeResponsePair> tmp=new ArrayList<>();
				challenges.forEach(pair->{
					ChallengeResponsePair newPair = new ChallengeResponsePair(pair);
					String t = newPair.challenge;
					newPair.challenge=newPair.response;
					newPair.response=t;
					tmp.add(newPair);
				});
				if (onlyRerversed) {
					challenges.clear();
				}
				challenges.addAll(tmp);
			}			
			if (sort) {
				sortChallengeResponsePairsByLengthAlpha(challenges);
			}
			if (random) {
				int length = challenges.stream().mapToInt(c -> c.challenge.length()*2 + c.response.length()*3).sum();
				Random rnd = new Random(challenges.size() + length);
				Collections.shuffle(challenges, rnd);
			}
			queued = createPimsleurStyledOutput(challenges, depth);
			writeChallengeResponsePairsTxt(outFile, queued);
			writeChallengeResponsePairsLyx(lyxBaseFile, queued);
		}
	}

	private void writeChallengeResponsePairsLyx(String lyxBaseFile, List<ChallengeResponsePair> queued)
			throws IOException {
		
		System.out.println("Queued: "+queued.size());
		double sets = Math.ceil((float) queued.size() / maxSetSize);
		System.out.println("\tSets: "+sets);
		int countPerSet = (int)Math.ceil((double)queued.size() / sets);
		System.out.println("\tPer set: "+countPerSet);
		List<List<ChallengeResponsePair>> lists = ListUtils.partition(queued,countPerSet);

		StringBuilder lyx_challenges_only = new StringBuilder();
		StringBuilder lyx_challenges_response = new StringBuilder();

		if (maxsets>0) {
			lists=lists.subList(0, maxsets);
		}
		
		lyx_challenges_only.append(LyxTemplate.docStart);
		lyx_challenges_response.append(LyxTemplate.docStart);

		int section = 1;
		for (List<ChallengeResponsePair> list : lists) {
			lyx_challenges_only.append(LyxTemplate.subsubsection(section));
			lyx_challenges_response.append(LyxTemplate.subsubsection(section));
			lyx_challenges_only.append(LyxTemplate.multiCol2_begin);
			lyx_challenges_response.append(LyxTemplate.multiCol2_begin);
			for (ChallengeResponsePair pair : list) {
				lyx_challenges_only.append(pair.toLyxCode(ResponseLayout.None));
				lyx_challenges_response.append(pair.toLyxCode(layout));
			}
			lyx_challenges_only.append(LyxTemplate.multiCol2_end);
			lyx_challenges_response.append(LyxTemplate.multiCol2_end);
			section++;
		}

		lyx_challenges_only.append(LyxTemplate.docEnd);
		lyx_challenges_response.append(LyxTemplate.docEnd);

		FileUtils.write(new File(lyxBaseFile + "-co.lyx"), lyx_challenges_only.toString(), "UTF-8");
		FileUtils.write(new File(lyxBaseFile + "-cr.lyx"), lyx_challenges_response.toString(), "UTF-8");
	}

	private void sortChallengeResponsePairsByLengthAlpha(List<ChallengeResponsePair> challenges) {
		Collections.sort(challenges, (a, b) -> {
			if (a.challenge.length() != b.challenge.length()) {
				return a.challenge.length() - b.challenge.length();
			}
			return a.challenge.compareTo(b.challenge);
		});
	}

	public static class ReplacementSet {
		public String field;
		public String[] replacements;
		public List<String> deck = new ArrayList<>();
	}

	private List<ChallengeResponsePair> parseChallengeResponsePairs(File file) throws IOException {
		LineIterator ifile = FileUtils.lineIterator(file);
		List<ChallengeResponsePair> list = new ArrayList<>();
		while (ifile.hasNext()) {
			String line = ifile.next();
			if (StringUtils.isBlank(line)) {
				continue;
			}
			if (StringUtils.strip(line).startsWith("#pragma:")) {
				if (line.contains("include-reversed")){
					includeRerversed=true;
				}
				if (line.contains("only-reversed")){
					onlyRerversed=true;
				}
				if (line.contains("layout=")) {
					String tmp = StringUtils.substringAfter(line, "layout=");
					tmp=StringUtils.strip(tmp);
					tmp=StringUtils.substringBefore(tmp, " ");
					tmp = StringUtils.strip(tmp);
					layout = ResponseLayout.valueOf(tmp);
				}
				if (line.contains("sep=")) {
					String tmp = StringUtils.substringAfter(line, "sep=");
					sep = StringUtils.strip(StringUtils.substringBefore(tmp, " "));
				}
				if (line.contains("nosort")) {
					sort = false;
				}
				if (line.contains("random")) {
					random = true;
					sort = false;
					depth = 1;
				}
				// maxSetSize
				if (line.contains("setsize=")) {
					String tmp = StringUtils.substringAfter(line, "setsize=");
					tmp = StringUtils.substringBefore(tmp, " ");
					try {
						maxSetSize = Integer.valueOf(tmp);
					} catch (NumberFormatException e) {
					}
				}
				if (line.contains("depth=")) {
					String tmp = StringUtils.substringAfter(line, "depth=");
					tmp = StringUtils.substringBefore(tmp, " ");
					try {
						depth = Integer.valueOf(tmp);
					} catch (NumberFormatException e) {
					}
				}
				if (line.contains("maxsets=")) {
					String tmp = StringUtils.substringAfter(line, "maxsets=");
					tmp = StringUtils.substringBefore(tmp, " ");
					try {
						maxsets = Integer.valueOf(tmp);
					} catch (NumberFormatException e) {
					}
				}
				continue;
			}
			if (StringUtils.strip(line).startsWith("#random:")) {
				String tmp = StringUtils.substringAfter(line, ":");
				String field = StringUtils.strip(StringUtils.substringBefore(tmp, "="));
				tmp = StringUtils.substringAfter(tmp, "=");
				String[] values = StringUtils.split(tmp, ",");
				ReplacementSet rset = new ReplacementSet();
				rset.field = field;
				rset.replacements = values;
				randomReplacements.put("<" + field + ">", rset);
				continue;
			}
			if (StringUtils.strip(line).startsWith("#")) {
				continue;
			}

			if (!line.contains("\t")) {
				System.err.println("BAD LINE (no tabs found): " + line);
				continue;
			}
			ChallengeResponsePair pair = new ChallengeResponsePair();
			String beforeTab = StringUtils.substringBefore(line, "\t");
			String afterTab = StringUtils.substringAfter(line, "\t");
			pair.challenge = StringUtils.strip(beforeTab);
			pair.response = StringUtils.strip(afterTab);
			pair.sep = sep;
			list.add(pair);
		}
		return list;
	}

	/**
	 * base pimsleur timings (seconds): 1(5^0), 5(5^1), 25(5^2), 125(5^3),
	 * 625(5^4), ...
	 */
	private List<ChallengeResponsePair> createPimsleurStyledOutput(List<ChallengeResponsePair> challenges,
			int intervals) {
		TimingSlots used_timing_slots = new TimingSlots();
		List<ChallengeResponsePair> queued = new ArrayList<>();
		for (ChallengeResponsePair pair : challenges) {
			int seconds_offset = 0;
			for (int interval = 0; interval < intervals; interval++) {
				ChallengeResponsePair new_pair = new ChallengeResponsePair(pair);
				int seconds_start = (int) Math.pow(5, interval) + seconds_offset;
				int length = (int) Math.ceil(new_pair.challenge.length() / 5f);
				while (used_timing_slots.isUsed(seconds_start, length)) {
					seconds_start++;
				}
				used_timing_slots.markUsed(seconds_start, length);
				new_pair.position = seconds_start;
				seconds_offset = seconds_start;
				queued.add(new_pair);
			}
		}
		Collections.sort(queued, (a, b) -> a.position - b.position);
		int maxtries = 10;
		redorder_dupes: do {
			if (maxtries-- < 0) {
				break redorder_dupes;
			}
			Iterator<ChallengeResponsePair> iq = queued.iterator();
			ChallengeResponsePair prev = null;
			while (iq.hasNext()) {
				ChallengeResponsePair a = iq.next();
				if (a.equals(prev)) {
					iq.remove();
					queued.add(a);
					continue redorder_dupes;
				}
				prev = a;
			}
			break;
		} while (true);
		int length = challenges.stream().mapToInt(c -> c.challenge.length() + c.response.length()).sum();
		Random rnd = new Random(depth + challenges.size() + length);
		for (ChallengeResponsePair new_pair : queued) {
			nextfield: for (String field : randomReplacements.keySet()) {
				String field_alt = "<=" + field.substring(1);
				if (!new_pair.challenge.contains(field) //
						&& !new_pair.response.contains(field) //
						&& !new_pair.challenge.contains(field_alt) //
						&& !new_pair.response.contains(field_alt)) {
					continue nextfield;
				}
				ReplacementSet rset = randomReplacements.get(field);
				if (rset.replacements.length == 0) {
					continue nextfield;
				}
				if (rset.deck.size() == 0) {
					rset.deck.addAll(Arrays.asList(rset.replacements));
					Collections.shuffle(rset.deck, rnd);
				}
				String tmp = rset.deck.remove(0);
				String a = StringUtils.substringBefore(tmp, "=");
				String b = StringUtils.substringAfter(tmp, "=");
				a = StringUtils.strip(a);
				b = StringUtils.strip(b);
				new_pair.challenge = new_pair.challenge.replace(field, a);
				new_pair.challenge = new_pair.challenge.replace(field_alt, b);
				new_pair.response = new_pair.response.replace(field, a);
				new_pair.response = new_pair.response.replace(field_alt, b);
			}
		}
		/**
		 * Remove back-to-back duplicates.
		 */
		Iterator<ChallengeResponsePair> iq = queued.iterator();
		ChallengeResponsePair prev = null;
		while (iq.hasNext()) {
			ChallengeResponsePair a = iq.next();
			if (a.equals(prev)) {
				iq.remove();
				continue;
			}
			prev = a;
		}
		return queued;
	}

	private void writeChallengeResponsePairsTxt(File outFile, List<ChallengeResponsePair> queued) throws IOException {
		FileUtils.writeLines(outFile, queued);
	}

	private void initFolders() {
		String baseDir = FileSystemView.getFileSystemView().getDefaultDirectory().getPath();
		subdircheck: {
			if (new File(baseDir, "Documents").isDirectory()) {
				baseDir = baseDir + "/Documents";
				break subdircheck;
			}
			if (new File(baseDir, "My Documents").isDirectory()) {
				baseDir = baseDir + "/My Documents";
				break subdircheck;
			}
		}
		inFolder = baseDir + "/ᏣᎳᎩ/ExerciseInterleavedMaker/input";
		outFolder = baseDir + "/ᏣᎳᎩ/ExerciseInterleavedMaker/output";
		new File(inFolder).mkdirs();
		new File(outFolder).mkdirs();
	}

}
