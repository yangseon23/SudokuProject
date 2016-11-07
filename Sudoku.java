package org.gs.sudoku;

import java.util.Random;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.JarURLConnection;

/**
 * Basic Algorithm is borrowed from www.sudokuessentials.com/create-sudoku.html
 *
 * Step 1: Create Sudoku Solution
 * Step 2: Remove Mirror Pairs
 * Step 3: Test For A Single Solution
 */
public class Sudoku
{
    public static class SudokuLevel
    {
        private final static ArrayList<SudokuLevel> _levels = new ArrayList<SudokuLevel>();

        private int initialGiven;
        private String name;

        public SudokuLevel(String name, int initialGiven)
        {
            this.name = name;
            this.initialGiven = initialGiven;

            synchronized (_levels)
            {
                if (_levels.contains(this))
                    throw new RuntimeException("Sudoku Level (" + name + ") is already defined");
                else
                    _levels.add(this);
            }
        }

        public String getName()
        {
            return name;
        }

        public int getInitialGiven()
        {
            return initialGiven;
        }

        public boolean equals(Object obj)
        {
            boolean equal;

            if (obj != null && this.getClass().isAssignableFrom(obj.getClass()))
                equal = name.equalsIgnoreCase(((SudokuLevel)obj).name);
            else
                equal = super.equals(obj);

            return equal;
        }

        public static SudokuLevel getLevel(String name)
        {
            SudokuLevel level = null;

            synchronized (_levels)
            {
                for (int i = 0; i < _levels.size() && level == null; i++)
                {
                    if (_levels.get(i).getName().equalsIgnoreCase(name))
                        level = _levels.get(i);
                }
            }

            return level;
        }
    }

    public static final SudokuLevel EASY_LEVEL = new SudokuLevel("Easy", 38);
    public static final SudokuLevel MEDIUM_LEVEL = new SudokuLevel("Medium", 32);
    public static final SudokuLevel HARD_LEVEL = new SudokuLevel("Hard", 21);
    public static final SudokuLevel EXTREME_LEVEL = new SudokuLevel("Extreme", 17);
    public static int MAX_SOLVE_MILLI = 2000;       // 2 seconds

    private static final int seedNum = 10;
    private static int extremeCount = 0;
    private static final int extremeDataSize = 26;  // 17 * 1.5 = 25.5

    private int _width;
    private SudokuLevel _level;
    private int _actualInitial;
    private byte[][] _problem;
    private byte[][] _intermediate;
    private byte[][] _answer;

    public Sudoku(int width, String levelName)
            throws Exception
    {
        SudokuLevel level = SudokuLevel.getLevel(levelName);
        if (level != null)
            init(width, level);
        else
            throw new Exception("Invalid Sudoku Level - " + levelName);
    }

    public Sudoku(int width, SudokuLevel level)
    {
        init(width, level);
    }

    public Sudoku(int width)
    {
        _width = width;
    }

    public SudokuLevel getLevel()
    {
        return _level;
    }

    public int getActualInitial()
    {
        return _actualInitial;
    }

    public byte[][] getProblem()
    {
        return _problem;
    }

    public byte[][] getAnswer()
    {
        return _answer;
    }

    private void init(int width, SudokuLevel level)
    {
        _width = width;
        _level = level;
        _problem = new byte[width][width];
        _intermediate = new byte[width][width];
        _answer = new byte[width][width];

        boolean done;
        do
        {
            if (EXTREME_LEVEL.equals(_level))
                done = initExtreme();
            else
                done = initProblem(level.getInitialGiven());
        } while (!done);

        _intermediate = null;
    }

    private boolean initProblem(int initial)
    {
        Random randPos = new Random();
        int count = 0;

        resetArray(_problem);
        resetArray(_intermediate);

        if (setNextValue(0, 0))
            count++;

        int total = _width * _width;
        while (count < seedNum)
        {
            int pos = Math.abs(randPos.nextInt()) % total;
            int x = pos % _width;
            int y = pos / _width;

            if (_problem[y][x] == 0 && setNextValue(y, x))
                count++;
        }

        boolean result = false;

        if (solve(false))
        {
            for (int y = 0; y < _width; y++)
            {
                System.arraycopy(_intermediate[y], 0, _answer[y], 0, _width);
                System.arraycopy(_intermediate[y], 0, _problem[y], 0, _width);
            }

            //printArray(_answer);

            int known = total;
            if (initial % 2 == 0)
            {
                _problem[_width/2][_width/2] = 0;
                known--;
            }

            boolean removable;
            int retry = 0;
            do
            {
                int pos = Math.abs(randPos.nextInt()) % total;
                int y = pos / _width;
                int x = pos % _width;

                if (_problem[y][x] != 0)
                {
                    _problem[y][x] = 0;
                    _problem[_width-y-1][_width-x-1] = 0;

                    removable = validateProblem();
                    if (removable)
                    {
                        known -= 2;
                        retry = 0;
                    }
                    else
                    {
                        _problem[y][x] = _answer[y][x];
                        _problem[_width-y-1][_width-x-1] = _answer[_width-y-1][_width-x-1];

                        if (++retry < 20)
                            removable = true;
                    }
                }
                else
                    removable = true;

                Thread.yield();
            } while (known > initial && removable);

            result = true;
            _actualInitial = known;
        }

        return result;
    }

    private boolean initExtreme()
    {
        boolean result = false;
        byte[][] data = new byte[_width][_width];

        if (loadExtreme(data))
        {
            for (int y = 0; y < _width; y++)
                System.arraycopy(data[y], 0, _problem[y], 0, _width);

            if (solve(false))
            {
                for (int y = 0; y < _width; y++)
                    System.arraycopy(_intermediate[y], 0, _answer[y], 0, _width);

                result = true;
            }
        }

        return result;
    }

    private boolean loadExtreme(byte[][] data)
    {
        boolean result = true;
        URL urlData = getClass().getResource("data/sudoku17.dat");
        InputStream is = null;

        try
        {
            if (urlData != null)
            {
                if (extremeCount == 0)
                    extremeCount = getExtremeDataCount(urlData);

                Random randPos = new Random();
                int pos = Math.abs(randPos.nextInt()) % extremeCount;

                is = urlData.openStream();
                is.skip(pos * extremeDataSize);

                byte[] buf = new byte[extremeDataSize];
                is.read(buf);

                byte[] buf2 = new byte[extremeDataSize * 2];
                for (int i = 0, i2 = 0; i < buf.length; i++, i2 += 2)
                {
                    buf2[i2] = (byte)((buf[i] >> 4) & 0x0F);
                    buf2[i2+1] = (byte)(buf[i] & 0x0F);
                }

                _actualInitial = 0;
                int i2 = 0;
                for (int y = 0; y < _width; y++)
                {
                    for (int x = 0; x < _width; x++)
                    {
                        if (buf2[i2] == y && buf2[i2+1] == x)
                        {
                            data[y][x] = buf2[i2+2];
                            i2 += 3;
                            _actualInitial++;
                        }
                        else
                            data[y][x] = 0;
                    }
                }
            }
            else
                result = false;
        }
        catch (Exception e)
        {
            result = false;
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    private int getExtremeDataCount(URL urlData)
            throws IOException
    {
        int dataCount;
        URLConnection urlCon = urlData.openConnection();

        if (urlCon instanceof JarURLConnection)
        {
            JarEntry je = ((JarURLConnection)urlCon).getJarEntry();
            dataCount = (int)(je.getSize() / extremeDataSize);
        }
        else
        {
            File fileData = new File(urlData.getPath());
            dataCount = (int)(fileData.length() / extremeDataSize);
        }

        return dataCount;
    }

    private boolean validateProblem()
    {
        for (int y = 0; y < _width; y++)
            System.arraycopy(_problem[y], 0, _intermediate[y], 0, _width);

        return solve(false) && !solve(true);
    }

    private void resetArray(byte[][] array)
    {
        for (int y = 0; y < _width; y++)
        {
            for (int x = 0; x < _width; x++)
                array[y][x] = 0;
        }
    }

    private boolean setNextValue(int y, int x)
    {
        byte value;
        int groupWidth = (int)Math.sqrt(_width);
        Random randValue = new Random();
        boolean found;
        ArrayList<Byte> values = new ArrayList<Byte>();

        do
        {
            value = (byte)(Math.abs(randValue.nextInt()) % _width + 1);
            found = values.contains(value) ;

            int ys = (y / groupWidth) * groupWidth;
            int xs = (x / groupWidth) * groupWidth;
            for (int yi = 0; yi < groupWidth && !found; yi++)
            {
                int yy = ys + yi;
                for (int xi = 0; xi < groupWidth && !found; xi++)
                {
                    int xx = xs + xi;
                    if (_problem[yy][xx] == value)
                        found = true;
                }
            }

            for (int yi = 0; yi < _width && !found; yi++)
            {
                if (_problem[yi][x] == value)
                    found = true;
            }

            for (int xi = 0; xi < _width && !found; xi++)
            {
                if (_problem[y][xi] == value)
                    found = true;
            }

            if (!found)
            {
                _problem[y][x] = value;
                _intermediate[y][x] = value;
            }
            else
                values.add(value);
        } while (found && values.size() < _width);

        return !found;
    }

    private boolean solve(boolean cont)
    {
        boolean solvable = true;
        int y, x;
        long startMilli = System.currentTimeMillis();

        if (cont)
        {
            y = _width - 1;
            x = _width - 1;

            do
            {
                while (x >= 0 && _problem[y][x] != 0)
                    x--;

                if (x < 0 && y > 0)
                {
                    y--;
                    x = _width - 1;
                }
            } while (x >= 0 && y >= 0 && _problem[y][x] != 0);
        }
        else
        {
            y = 0;
            x = 0;

            _intermediate = new byte[_width][_width];
            for (int yi = 0; yi < _width; yi++)
                System.arraycopy(_problem[yi], 0, _intermediate[yi], 0, _width);
        }

        while (solvable)
        {
            if (x < 0 || y < 0 || System.currentTimeMillis() - startMilli > MAX_SOLVE_MILLI)
                solvable = false;
            else if (_problem[y][x] != 0 || findValue(y, x))
            {
                if (++x >= _width)
                {
                    if (++y >= _width)
                        break;
                    x = 0;
                }
            }
            else
            {
                _intermediate[y][x--] = 0;

                do
                {
                    while (x >= 0 && _problem[y][x] != 0)
                        x--;

                    if (x < 0 && y > 0)
                    {
                        if (y == 1)
                            y = 0;
                        else
                    	    y--;
                        x = _width - 1;
                    }
                } while (x >= 0 && y >= 0 && _problem[y][x] != 0);
            }
        }

        return solvable;
    }

    private boolean findValue(int y, int x)
    {
        boolean found;
        int groupWidth = (int)Math.sqrt(_width);
        byte value = _intermediate[y][x];

        do
        {
            found = ++value > _width;

            for (int xi = 0; xi < _width && !found; xi++)
            {
                if (_intermediate[y][xi] == value)
                    found = true;
            }

            if (!found)
            {
                int ys = (y / groupWidth) * groupWidth;
                int xs = (x / groupWidth) * groupWidth;
                for (int yi = 0; yi < groupWidth && !found; yi++)
                {
                    int yy = ys + yi;
                    for (int xi = 0; xi < groupWidth && !found; xi++)
                    {
                        int xx = xs + xi;
                        if (_intermediate[yy][xx] == value)
                            found = true;
                    }
                }

                for (int yi = 0; yi < _width && !found; yi++)
                {
                    if (_intermediate[yi][x] == value)
                        found = true;
                }
            }

            if (!found)
                _intermediate[y][x] = value;
        } while (found && value <= _width);

        return !found;
    }

    private void printArray(byte[][] values)
    {
        StringBuffer sb = new StringBuffer();

        for (int y = 0; y < _width; y++)
        {
            for (int x = 0; x < _width; x++)
                sb.append(values[y][x]).append(' ');
            sb.append("\r\n");
        }

        System.out.println(sb.toString());
    }

    private byte[] encodeRLE(byte[] line)
    {
        byte[] encoded = new byte[extremeDataSize];
        int o = 0;
        boolean remain4bits = false;

        for (int i = 0; i < line.length; i++)
        {
            if (line[i] != '0')
            {
                int y = i / _width;
                int x = i % _width;
                
                if (remain4bits)
                {
                    encoded[o++] |= y & 0x0F;
                    encoded[o++] = (byte)((x << 4) | (line[i]-'0'));
                    remain4bits = false;
                }
                else
                {
                    encoded[o++] = (byte)((y << 4) | x);
                    encoded[o] = (byte)((line[i]-'0') << 4);
                    remain4bits = true;
                }
            }
        }

        return encoded;
    }

    private void encodeData(String inPath, String outPath)
            throws Exception
    {
        BufferedReader br = new BufferedReader(new FileReader(inPath));
        String line;
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outPath));

        while ((line = br.readLine()) != null && line.length() > 0)
        {
            byte[] encoded = encodeRLE(line.getBytes());
            bos.write(encoded);
        }

        br.close();
        bos.close();
    }

    public static void main(String[] args)
            throws Exception
    {
        if (args.length < 2)
        {
            Sudoku sudoku = new Sudoku(9, EXTREME_LEVEL);

            sudoku.printArray(sudoku._problem);

            if (sudoku.solve(false))
            {
                System.out.println("\r\n### Solution ###");
                sudoku.printArray(sudoku._intermediate);
                if (sudoku.solve(true))
                {
                    System.out.println("\r\n### Solution ###");
                    sudoku.printArray(sudoku._intermediate);
                }
                System.out.println("Found possible solutions !!!");
            }
            else
                System.out.println("Unsolvable !!!");
        }
        else
        {
            Sudoku sudoku = new Sudoku(9);
            sudoku.encodeData(args[0], args[1]);
        }
    }
}
