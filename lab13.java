import java.io.*;
import java.util.*;

public class lab13 {

    private ArrayList<Integer> data = new ArrayList<>();

    public void readData(String filename) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            while ((line = br.readLine()) != null) {
                data.add(Integer.parseInt(line.trim()));
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long getTotalCount() {
        return data.stream().count();
    }

    public long getOddCount() {
        return data.stream().filter(x -> x % 2 != 0).count();
    }

    public long getEvenCount() {
        return data.stream().filter(x -> x % 2 == 0).count();
    }

    public long getDistinctGreaterThanFiveCount() {
        return data.stream().filter(x -> x > 5).distinct().count();
    }

    public Integer[] getResult1() {
        return data.stream()
            .filter(x -> x > 5 && x < 50 && x % 2 == 0)
            .sorted()
            .toArray(Integer[]::new);
    }

    public Integer[] getResult2() {
        return data.stream()
            .map(x -> x * x * 3)
            .limit(50)
            .toArray(Integer[]::new);
    }

    public Integer[] getResult3() {
        return data.stream()
            .filter(x -> x % 2 != 0)
            .map(x -> x * 2)
            .sorted()
            .skip(20)
            .distinct()
            .toArray(Integer[]::new);
    }
}
