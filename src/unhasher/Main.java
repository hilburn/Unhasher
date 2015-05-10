package unhasher;

import org.ardverk.collection.PatriciaTrie;
import org.ardverk.collection.StringKeyAnalyzer;
import org.ardverk.collection.Trie;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class Main implements Runnable{

    private static final Map<Integer,char[]> values = new HashMap<Integer, char[]>();
//    private static final String regex = "( |\\.|,|\")+";
    private static final String regex = " +";
//    private static final String invalidRegex = "(?:\\.|,)[^ ]|[a-z][A-Z]|\\w\"\\w";
    private static final String invalidRegex = "(?:[A-Z]\\w)|(?:  )";
    private static final String finalRegex = "(?:[A-Z]\\w)|(?:  )|(?: $)";
//    private static final Set<Character> checkChars = new HashSet<Character>(Arrays.asList(' ', '.', ',', '"'));
    private static final Set<Character> checkChars = new HashSet<Character>(Arrays.asList(' '));
    private static final Pattern ignore = Pattern.compile(regex);
    private static final Pattern invalid = Pattern.compile(invalidRegex);
    private static final Pattern invalidFinal = Pattern.compile(finalRegex);
    private static int MAX_OVERFLOW = 1000000000;
    private static Trie<String, Boolean> dictionaryTrie = new PatriciaTrie<String, Boolean>(StringKeyAnalyzer.BYTE);
    private static List<String> results = new ArrayList<String>();
    static long time;
    private byte mine;
    private static final BigInteger THIRTY_ONE = new BigInteger(new byte[]{31});
    private static final BigInteger MIN = new BigInteger(new byte[]{'A'-1});
    private static final BigInteger MAX = new BigInteger(new byte[]{'z'});
    private static final BigInteger INT_RANGE = new BigInteger(new byte[]{0xF,0xF,0xF,0xF,0xF,0xF,0xF,0xF});
    private static final BigInteger HASH = new BigInteger(new byte[]{0x7,0x8, 0x6,0x7,0xE, 0xB, 0x0, 0xB});
    private static final BigInteger THREADS = new BigInteger(new byte[]{8});
    private static final BigInteger STEP = THREADS.multiply(HASH);
    private static final Map<Character, BigInteger> INTEGER_MAP = new HashMap<Character, BigInteger>();

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
            INTEGER_MAP.put(letters.charAt(i), new BigInteger(new byte[]{(byte)letters.charAt(i)}));
        }

        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
        File file = new File(location.getPath());
        while(file.getPath().contains("out")) file=file.getParentFile();

        getDictionary(file);

        System.out.println("Time to Initialise: " + ((System.nanoTime() - time) / 1000000) + "ms");
        time = System.nanoTime();
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i<THREADS.intValue(); i++)
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
        mine = (byte)thread;
    }

    private void testStrings()
    {
        HashPair pair = new HashPair(19);
        int printed = 0;
        for (BigInteger i = pair.min.add(new BigInteger(new byte[]{mine}).multiply(HASH)); i.compareTo(pair.max) < 0 ; i=i.add(STEP))
        {
            checkPotentialResults(i, "");
            if (mine == 0)
            {
                int now = (int)((System.nanoTime() - time) / 1000000000);
                if (printed != now && now % 600 == 0)
                {
                    printed = now;
                    System.out.println(i.toString() + "/" + pair.max.toString() + " complete after " + now + "s");
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

    private static void checkPotentialResults(BigInteger hashToTest, String result)
    {
        if (hashToTest.compareTo(BigInteger.ZERO) == 0)
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
            checkPotentialResults(hashToTest.subtract(INTEGER_MAP.get(test)).divide(THIRTY_ONE), result + test);
        }
    }

    private static char[] getLastChars(BigInteger hashcode)
    {
        return values.get(hashcode.intValue() % 31);
    }

    @Override
    public void run()
    {
        testStrings();
    }

    private static class HashPair
    {
        private BigInteger min = BigInteger.ZERO;
        private BigInteger max = BigInteger.ZERO;
        private int index;

        public HashPair(int length)
        {
            index = length;
            for (int i = 0; i < length; i++)
            {
                min = min.multiply(THIRTY_ONE).add(MIN);
                max = max.multiply(THIRTY_ONE).add(MAX);
            }
        }
    }
}
