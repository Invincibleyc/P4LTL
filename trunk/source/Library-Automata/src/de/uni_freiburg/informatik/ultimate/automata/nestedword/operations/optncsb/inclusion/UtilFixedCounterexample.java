/*
 * Copyright (C) 2017 Yong Li (liyong@ios.ac.cn)
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Automata Library.
 *
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.optncsb.inclusion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWord;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi.NestedLassoRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi.NestedLassoWord;
/**
 * This is only used to fix the counterexample in the experiments
 * **/

public class UtilFixedCounterexample<LETTER, STATE> {
	
	private final String PATH = "counterexamples";
	private final String SEPARATOR = "----";
	
	private final Map<String, LETTER> mMap = new HashMap<>();
	
	public UtilFixedCounterexample() {
	}
	
	
	public NestedLassoRun<LETTER, STATE> getNestedLassoRun(
			AutomataLibraryServices services,
			INestedWordAutomaton<LETTER, STATE> automaton, String name, int iteration) throws AutomataOperationCanceledException {
		final String fileName = PATH + "/" + name + iteration;
		File file = new File(fileName);
		if(!file.exists()) {
			File dir = new File(PATH);
			if(!dir.exists()) dir.mkdirs();
			return null;
		}
		mMap.clear();
		BufferedReader reader = null;
        try {
        	reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        }catch(FileNotFoundException e) {
            e.printStackTrace();
        }
        if(! hasOnlyInternalLetters(automaton)) {
        	return null;
        }
        // initialize letters
        addLettersToStringMap(mMap, automaton.getVpAlphabet().getInternalAlphabet());
        // first stem
        boolean isStem = true;
        boolean isOk = true;
        String line = null;
        NestedWord<LETTER> stem = new NestedWord<>();
        NestedWord<LETTER> loop = new NestedWord<>();
        try {
			while((line = reader.readLine()) != null) {
				if(line.startsWith(SEPARATOR)) {
					isStem = false;
					continue;
				}
				LETTER letter = mMap.get(line);
				if(letter == null) {
					isOk = false;
					break;
				}
				NestedWord<LETTER> suffix = new NestedWord<>(letter, NestedWord.INTERNAL_POSITION);
				if(isStem) {
					stem = stem.concatenate(suffix);
				}else {
					loop = loop.concatenate(suffix);
				}
			}
			if(reader != null) reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        NestedLassoWord<LETTER> word = null;
        if(isOk) {
        	word = new NestedLassoWord<>(stem, loop);
        }
        if(word == null) return null;
        GetLassoRunFromLassoWord<LETTER, STATE> getter = new GetLassoRunFromLassoWord<>(services, automaton, word);
        NestedLassoRun<LETTER, STATE> run = getter.getNestedLassoRun();
        if(run == null) {
        	System.err.println("Wrong automaton");
        	System.exit(-1);
        }
        return run;
	}
	
	
	private final void addLettersToStringMap(Map<String, LETTER> map,
			final Set<LETTER> letters) {
		for (LETTER letter : letters) {
			String letterStr = letter.toString();
			if (map.containsKey(letterStr)) {
				new AssertionError("Letters with the same string: " + letter);
			} else {
				map.put(letterStr, letter);
			}
		}
	}
	
	public final void writeNestedLassoRun(INestedWordAutomaton<LETTER, STATE> automaton, NestedLassoRun<LETTER, STATE> lassoRun, String name, int iteration) {
		//we donot store words with call and return alphabets
		if(!hasOnlyInternalLetters(automaton)) {
			return ;
		}
		final String fileName = PATH + "/" + name + iteration;
        File file = new File(fileName);
		if(!file.exists()) {
			return;
		}
        writeWordToFile(lassoRun.getNestedLassoWord(), file);
	}
	
	private final void writeWordToFile(NestedLassoWord<LETTER> word, File file) {
		PrintStream out = null;
		try {
			out = new PrintStream(file);
			writeLettersToFile(out, word.getStem().asList());
			out.println(SEPARATOR);
			writeLettersToFile(out, word.getLoop().asList());
			if(out != null) out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private final void writeLettersToFile(PrintStream out, List<LETTER> word) {
		for (LETTER letter : word) {
			out.println(letter.toString()); // one line one letter
		}
	}
	
	private final boolean hasOnlyInternalLetters(INestedWordAutomaton<LETTER, STATE> automaton) {
        if(automaton.getVpAlphabet().getCallAlphabet().isEmpty()
        && automaton.getVpAlphabet().getReturnAlphabet().isEmpty()) {
        	return true;
        }
        return false;
	}

}
