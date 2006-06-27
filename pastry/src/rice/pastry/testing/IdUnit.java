package rice.pastry.testing;

import rice.pastry.Id;

public class IdUnit {

  /**
   * @param args
   */
  public static void main(String[] args) {
    Id id0 = Id.build("0");
    Id id1 = Id.build("1");
    Id id2 = Id.build("2");
    Id id3 = Id.build("3");
    Id id4 = Id.build("4");
    Id id5 = Id.build("5");
    Id id6 = Id.build("6");
    Id id7 = Id.build("7");
    Id id8 = Id.build("8");
    Id id9 = Id.build("9");
    Id ida = Id.build("A");
    Id idb = Id.build("B");
    Id idc = Id.build("C");
    Id idd = Id.build("D");
    Id ide = Id.build("E");
    Id idf = Id.build("F");

    test("clockwise 0-1",id0.clockwise(id1));
    test("clockwise 1-0",!id1.clockwise(id0));
    test("clockwise 0-8",id0.clockwise(id8));
    test("clockwise 8-0",id8.clockwise(id0));
    test("clockwise 0-9",!id0.clockwise(id9));
    test("clockwise 9-0",id9.clockwise(id0));

    test("between 0-1-2",id1.isBetween(id0,id2));
    test("between 2-1-0",!id1.isBetween(id2,id0));
    test("between 0-4-2",!id4.isBetween(id0,id2));
    test("between 2-4-0",id4.isBetween(id2,id0));
    test("between F-0-1",id0.isBetween(idf,id1));
    test("between 1-0-F",!id0.isBetween(id1,idf));
  }
  
  public static void test(String msg, boolean result) {
    System.out.println(msg + ": " + (result ? "PASS" : "FAIL"));
  }

}
