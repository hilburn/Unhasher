package unhasher;

import org.ardverk.collection.PatriciaTrie;
import org.ardverk.collection.StringKeyAnalyzer;
import org.ardverk.collection.Trie;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class Main implements Runnable{

    private static final Map<Integer,char[]> values = new HashMap<Integer, char[]>();
//    private static final String regex = "( |\\.|,|\")+";
    private static final String regex = " +";
//    private static final String invalidRegex = "(?:\\.|,)[^ ]|[a-z][A-Z]|\\w\"\\w";
    private static final String invalidRegex = "[A-Z]\\w";
    private static final String finalRegex = "(?:[A-Z]\\w)|(?: $)";
//    private static final Set<Character> checkChars = new HashSet<Character>(Arrays.asList(' ', '.', ',', '"'));
    private static final Set<Character> checkChars = new HashSet<Character>(Arrays.asList(' '));
    private static final Pattern ignore = Pattern.compile(regex);
    private static final Pattern invalid = Pattern.compile(invalidRegex);
    private static final Pattern invalidFinal = Pattern.compile(finalRegex);
    private static final long HASH = 0x7867EB0B;
    private static final long THREADS = 1000;
    private static int MAX_OVERFLOW = 1000000000;
    private static Trie<String, Boolean> dictionaryTrie = new PatriciaTrie<String, Boolean>(StringKeyAnalyzer.BYTE);
    private static List<String> results = new ArrayList<String>();
    static long time;
    private long mine;

    public static void main(String[] args)
    {
        time = System.nanoTime();
        String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ '";
        for (int i = 0; i < letters.length(); i++)
        {
            int hash = letters.charAt(i) % 31;
            String val = values.containsKey(hash)? new String(values.get(hash)) : "";
            val += letters.charAt(i);
            values.put(hash, val.toCharArray());
        }

        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
        File file = new File(location.getPath());
        while(file.getPath().contains("out")) file=file.getParentFile();

        getDictionary(file);
        System.out.println("Time to Initialise: " + ((System.nanoTime() - time) / 1000000) + "ms");
        time = System.nanoTime();

        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i<(int)THREADS; i++)
        {
            Thread thread = new Thread(new Main(i));
            threads.add(thread);
            thread.start();
        }

        for(Thread thread : threads)
            try
            {
                thread.join();
            } catch (InterruptedException e)
            {
            }

        writeFile(file, results);
    }



    private static void getDictionary(File file)
    {
        File words = new File(file, "words.txt");
        Set<String> newDictionary;
        if (!words.exists())
        {
            newDictionary = new TreeSet<String>();
            File dictionaries = new File(file, "\\dictionaries");
            for (File dictionary : dictionaries.listFiles())
            {
                readFile(dictionary, newDictionary, true);
            }
            newDictionary.remove("");
            writeFile(words, newDictionary);
        }
        else
        {
            newDictionary = new HashSet<String>();
            readFile(words, newDictionary, false);
        }
        addDictionaryToTrie(newDictionary, dictionaryTrie);
    }

    private static void addDictionaryToTrie(Set<String> dictionary, Trie<String, Boolean> dictionaryTrie)
    {
        for (String string : dictionary)
        {
            dictionaryTrie.put(new StringBuilder(string).reverse().toString(), true);
        }
    }

    private static void readFile(File file, Set<String> set, boolean lowercase)
    {
        try
        {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String str;
            while ((str = in.readLine()) != null)
            {
                set.add(lowercase?str.toLowerCase():str);
            }
            in.close();
        } catch (IOException ignored)
        {
        }
    }

    private static void writeFile(File file, Collection<String> strings)
    {
        try {
            if (!file.exists()) file.createNewFile();
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
            for (String entry : strings) writer.write(entry + "\n");
            writer.close();
        }catch (Exception ignore){
        }
    }

    private Main(int thread)
    {
        mine = thread;
    }

    private void testStrings()
    {
        long overflow = (long)Integer.MAX_VALUE - Integer.MIN_VALUE + 1;
        int percent = MAX_OVERFLOW/1000;
        int printed = 0;
        for (long i = 0; i<MAX_OVERFLOW; i++)
        {
            if (i % THREADS == mine)
            {
                long hashToTest = i * overflow + HASH;
                checkPotentialResults(hashToTest, "");
                if (mine == 0 && (i + 1) % percent == 1)
                {
                    int now = (int)((System.nanoTime() - time) / 1000000000);
                    if (now == printed) continue;
                    printed = now;
                    System.out.println(((double)(i) / (percent * 10)) + "% complete after " + now + "s");
                }
            }
        }
    }

    private static boolean isFullyValid(String sentence)
    {
        if (invalidFinal.matcher(sentence).find()) return false;
        for (String word : sentence.split(regex))
        {
            if (!isValidWord(word)) return false;
        }
        return true;
    }

    private static boolean isValidWord(String word)
    {
        return dictionaryTrie.containsKey(word.toLowerCase());
    }

    private static boolean isPotentiallyValid(String sentence)
    {
        if (invalid.matcher(sentence).find()) return false;
        String[] split = sentence.split(regex);
        if (split.length < 1) return false;
        if (!isValidStubWord(split[split.length - 1])) return false;
        for (int i = 0; i < split.length - 1; i++)
        {
            if (!isValidWord(split[i])) return false;
        }
        return true;
    }

    private static boolean isValidStubWord(String s)
    {
        return dictionaryTrie.prefixMap(s.toLowerCase()).size()>0;
    }

    private static void checkPotentialResults(long hashToTest, String result)
    {
        if (hashToTest == 0)
        {
            if (isFullyValid(result))
            {
                results.add(new StringBuilder(result).reverse().toString());
                System.out.println(new StringBuilder(result).reverse().toString());
            }
            return;
        }
        char[] lastChars = getLastChars(hashToTest);
        if (lastChars == null || !isPotentiallyValid(result)) return;
        for (char test : lastChars)
        {
            checkPotentialResults((hashToTest-test)/31, result + test);
        }
    }

    private static char[] getLastChars(long hashcode)
    {
        return values.get((int)(hashcode % 31));
    }


    @Override
    public void run()
    {
        testStrings();
    }
}
