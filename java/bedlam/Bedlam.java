package bedlam;

import java.io.FileWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Bedlam {

    // In two's complements all bits set is -1 (full cube)
    final static long FULL = -1l;

    // Array of shape num x blocks x {x,y,z}
    static final int [][][] shapes = {
        {{0,0,0},{0,1,0},{0,2,0},{1,1,0},{1,1,1}},
        {{0,0,0},{0,1,0},{0,2,0},{1,0,0},{0,1,1}},
        {{0,0,0},{0,1,0},{0,2,0},{1,0,1},{0,0,1}},
        {{0,0,0},{0,1,0},{0,2,0},{0,0,1},{1,0,0}},
        {{0,0,0},{0,1,0},{0,1,1},{1,1,1},{1,2,1}},
        {{0,0,0},{0,1,0},{0,1,1},{0,2,1},{1,1,1}},
        {{0,1,0},{1,0,0},{1,1,0},{1,2,0},{2,1,0}},
        {{0,0,0},{0,1,0},{0,1,1},{1,1,1}},
        {{0,0,0},{0,1,0},{0,2,0},{1,1,0},{0,1,1}},
        {{0,1,0},{0,2,0},{1,0,0},{1,1,0},{2,1,0}},
        {{0,0,0},{0,1,0},{1,0,0},{0,1,1},{0,2,1}},
        {{0,0,0},{0,1,0},{0,2,0},{1,0,0},{0,2,1}},
        {{0,1,0},{0,2,0},{1,0,0},{1,1,0},{2,0,0}}
    };

    // One possible solution (need to verify)
    static final int [][][] SOLN = {
        {{1, 2, 0},{2, 1, 1},{2, 2, 0},{2, 2, 1},{3, 2, 0}},
        {{0, 1, 0},{0, 2, 0},{0, 2, 1},{0, 3, 0},{1, 1, 0}},
        {{0, 2, 2},{1, 2, 2},{1, 3, 0},{1, 3, 1},{1, 3, 2}},
        {{0, 2, 3},{0, 3, 1},{0, 3, 2},{0, 3, 3},{1, 3, 3}},
        {{1, 1, 2},{2, 0, 3},{2, 1, 2},{2, 1, 3},{3, 0, 3}},
        {{0, 0, 3},{0, 1, 2},{0, 1, 3},{1, 1, 3},{1, 2, 3}},
        {{0, 0, 2},{1, 0, 1},{1, 0, 2},{1, 0, 3},{2, 0, 2}},
        {{2, 2, 3},{2, 3, 3},{3, 3, 2},{3, 3, 3}},
        {{1, 0, 0},{2, 0, 0},{2, 0, 1},{2, 1, 0},{3, 0, 0}},
        {{3, 1, 0},{3, 1, 1},{3, 2, 1},{3, 2, 2},{3, 3, 1}},
        {{0, 0, 0},{0, 0, 1},{0, 1, 1},{1, 1, 1},{1, 2, 1}},
        {{2, 2, 2},{2, 3, 0},{2, 3, 1},{2, 3, 2},{3, 3, 0}},
        {{3, 0, 1},{3, 0, 2},{3, 1, 2},{3, 1, 3},{3, 2, 3}}
    };

    // Generate positions
    long [][] positions;
    Bedlam() {
        int i=0;
        positions = new long[shapes.length][];
        for (int[][] shape : shapes) {
            Set<Long> posSet = new HashSet<Long>();
            for (int[][] tshape : translate(shape)) {
                addPositions(posSet, tshape);
            }
            positions[i++] = toArray(posSet);
        }
        assert i==positions.length;
    }

    // Search for solutions, return an array of offsets inside the positions array
    long solnCnt;
    final int[] solnPos = new int[shapes.length];
    void search() {
        solnCnt = System.currentTimeMillis();
        search(0l,0);
    }

    // Work through each shape discarding those that clash with a filled position
    void search(long cube, int pos) {
        if (cube == FULL) {
            System.out.println("Found solution after " + ((System.currentTimeMillis() - solnCnt)/1000) + "s");
            for (int p=0;p<solnPos.length;p++) System.out.println(toString(decode(positions[p][solnPos[p]])));
            return; // System.exit(1);
        }
        long posn[] = positions[pos];
        for (int i=0;i<posn.length;i++) {
            if ((cube & posn[i]) == 0l) {
                long newCube = cube | posn[i];
                if (!prune(newCube, pos+1)) {
                    solnPos[pos] = i;
                    search (newCube, pos+1);
                }
            }
        }
    }

    // Prune a search if either
    // - There is a piece that can no longer fit
    // - There is an enclosed hole in the cube
    boolean prune(long cube, int pos) {
        long testCube = cube;
        for (int p=pos;p<positions.length;p++) {
            boolean used = false;
            for (long shapePos : positions[p]) {
                if ((shapePos & cube) == 0l) {
                    used = true;
                    if ((testCube |= shapePos) == FULL) break;
                }
            }
            if (!used) return true;     // Piece does not fit
        }
        return !(testCube == FULL);     // Will be full if there were no holes
    }

    // Pre-allocate space or we get killed
    final int[][] offsets = new int[shapes.length][shapes.length];

    void search2() {
        solnCnt = System.currentTimeMillis();
        search2(0l,0);
    }

    // Work through each shape discarding those that clash with a filled position
    void search2(long cube, int pos) {

        long posn[] = positions[pos];
        int[] offset = offsets[pos];

        for (int i=offset[pos];i<posn.length;i++) {
            if ((cube & posn[i]) == 0l) {
                long newCube = cube | posn[i];
                if (newCube == FULL) {
                    System.out.println("Found solution after " + ((System.currentTimeMillis() - solnCnt)/1000) + "s");
                    for (int p=0;p<solnPos.length;p++) System.out.println(toString(decode(positions[p][solnPos[p]])));
                    System.exit(1);
                } else {
                    // Calculate new offsets based on newCube
                    int[] newOffset = offsets[pos+1]; // new int[offset.length];
                    boolean prune = false;
                    for (int offPos = pos+1; offPos < positions.length; offPos++) {
                        long offPosn[] = positions[offPos];
                        int o=offset[offPos];
                        for (int j=o;j<offPosn.length;j++) {
                            if ((offPosn[j] & newCube) != 0l) {
                                // Piece no longer fits so swap under offset
                                if (j>o) {
                                    long tmp = offPosn[j];
                                    offPosn[j] = offPosn[o];
                                    offPosn[o] = tmp;
                                }
                                o++;
                            }
                        }
                        if (o == offPosn.length) {
                            prune = true;
                            break;
                        }
                        newOffset[offPos] = o;
                    }

                    // Prune
                    if (!prune && !prune2(newCube, pos+1, newOffset)) {
                        solnPos[pos] = i;
                        search2 (newCube, pos+1); // , newOffset);
                    }
                }
            }
        }
    }

    // Prune a search if either
    // - There is a piece that can no longer fit
    // - There is an enclosed hole in the cube
    boolean prune2(long cube, int pos, int[] offset) {
        long testCube = cube;
        for (int p=pos;p<positions.length;p++) {
            long posn[] = positions[p];
            for (int i=offset[p];i<posn.length;i++) {
                if ((testCube |= posn[i]) == FULL) return false;
            }
        }
        return true;
    }

    // Convert long set to array
    long[] toArray(Set<Long> set) {
        int c=0;
        long[] a = new long[set.size()];
        for (long l : set) a[c++] = l;
        return a;
    }

    // Encode shape array as long
    static long encode (int [][] shape) {
        long l = 0l;
        for (int[] block : shape) {
            l |= (1l << (block[0] * (SIDE*SIDE) + block[1] * SIDE + block[2]));
        }
        return l;
    }

    // Decode shape long as array
    static int[][] decode(long shape) {
        int [][] xblocks = new int[64][];
        int c=0;
        for (int i=0;i<64;i++) {
            if ((shape & (1l << i)) != 0l) {
                long b = i;
                int x = (int) (b / (SIDE*SIDE));    b -= x * (SIDE*SIDE);
                int y = (int) (b / SIDE);           b -= (y * SIDE);
                int z = (int) (b);
                xblocks[c++] = new int[] { x, y, z };
            }
        }
        int [][] blocks = new int[c][];
        System.arraycopy(xblocks, 0, blocks, 0, c);
        return blocks;
    }

    // Render a string
    static String toString(int [][] shape) {
        StringBuilder sb = new StringBuilder();
        String c = "";
        for (int[] block : shape) {
            sb.append(c + Arrays.toString(block)); c=",";
        }
        return sb.toString();
    }

    // Helper to get max dimension
    int max(int shape[][], int dim) {
        int max = Integer.MIN_VALUE;
        for (int [] block : shape) {
            if (block[dim] > max) max = block[dim];
        }
        return max;
    }

    // Helper to get min dimension
    int min(int shape[][], int dim) {
        int min = Integer.MAX_VALUE;
        for (int [] block : shape) {
            if (block[dim] < min) min = block[dim];
        }
        return min;
    }

    // Cube is SIDE x SIDE x SIDE
    final static int SIDE = 4;

    // Return all 6x4 possible rotations of the shape
    int[][][] translate (int [][] shape) {
        int i=0;
        int [][][] trans = new int[24][][];

        // Get all y axis tranlations
        shape = rotate(shape,1); i = addTrans(shape, trans, i);
        shape = rotate(shape,1); i = addTrans(shape, trans, i);
        shape = rotate(shape,1); i = addTrans(shape, trans, i);
        shape = rotate(shape,1); i = addTrans(shape, trans, i);

        // Add missing z axis translations
        shape = rotate(shape,2); i = addTrans(shape, trans, i);
        shape = rotate(shape,2);
        shape = rotate(shape,2); i = addTrans(shape, trans, i);
        shape = rotate(shape,2);

        // Check total and return
        assert (i==trans.length);
        return trans;
    }

    // Add all rotations about an axis
    int addTrans(int [][] shape, int [][][]trans, int i) {
        trans[i] = rotate(shape,0)     ;i++;
        trans[i] = rotate(trans[i-1],0);i++;
        trans[i] = rotate(trans[i-1],0);i++;
        trans[i] = rotate(trans[i-1],0);i++;
        return i;
    }

    // Rotate shape about y axis
    int [][] rotate (int [][] shape, int axis) {
        int[][] rot = new int[shape.length][];
        for (int i=0;i<shape.length;i++) {
            rot[i] = rotate(shape[i], axis);
        }
        return rot;
    }

    // Block rotators
    int [] rotate (int [] block, int axis) {
        switch (axis) {
            case 0: return new int[] { block[0], block[2],-block[1]};
            case 1: return new int[] {-block[2], block[1], block[0]};
            case 2: return new int[] { block[1],-block[0], block[2]};
        }
        throw new IllegalArgumentException();
    }

    // Return all positions the shape could occupy in the cube
    void addPositions (Set<Long> set, int [][] shape) {

        int xMin = min(shape,0);
        int xWidth = max(shape,0) - xMin + 1;
        int yMin = min(shape,1);
        int yWidth = max(shape,1) - yMin + 1;
        int zMin = min(shape,2);
        int zWidth = max(shape,2) - zMin + 1;

        // Add all encoded positions into array
        for (int x=0;x<=SIDE-xWidth;x++) {
            for (int y=0;y<=SIDE-yWidth;y++) {
                for (int z=0;z<=SIDE-zWidth;z++) {
                     set.add(encode(shift(shape,x-xMin,y-yMin,z-zMin)));
                }
            }
        }
    }

    // Return shape shifted by x,y,z amounts
    int [][] shift(int [][] shape, int x, int y, int z) {
        int[][] shift = new int [shape.length][];
        int i=0;
        for (int [] block : shape) {
            shift[i++] = new int[] { block[0]+x, block[1]+y, block[2]+z };
        }
        assert i==shape.length;
        return shift;
    }

    // Render blocks as SVG
    public static void toSVG(int[][][] soln) throws Exception {

        Writer out =  new FileWriter("bedlam.svg");

        // Write out header section
        out.write("<svg width='1000' height='1000' viewBox = '0 0 5000 5000' xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'>\n");
        out.write("<defs>\n");
        out.write("<polygon id='ppanel' points='0 0, 0 100, 86.6 50, 86.6 -50'/>\n");
        out.write("<g id='box'>\n");
        out.write("<use xlink:href='#ppanel' transform='rotate(-120)'/>\n");
        out.write("<use xlink:href='#ppanel' transform='rotate(0)'/>\n");
        out.write("<use xlink:href='#ppanel' transform='rotate(120)'/>\n");
        out.write("</g>\n");
        out.write("<g id='block'>\n");
        out.write("<use xlink:href='#box' style='fill:pink;'/>\n");
        out.write("</g>\n");
        out.write("<g id='topcube'>\n");
        out.write("<use xlink:href='#box' style='stroke-dasharray:1,3;' transform='translate(0,100),scale(4),translate(0,-100)' />\n");
        out.write("</g>\n");
        out.write("<g id='cube'>\n");
        out.write("<use xlink:href='#box' style='stroke-dasharray:1,3;' transform='rotate(180),translate(0,-100),scale(4),translate(0,100)' />\n");
        out.write("</g>\n");

        // Write shape definitions
        for (int b=0;b<soln.length;b++) {
            int [][] shape = soln[b];
            out.write("<g id='block" + b + "'>\n");
            for (int x=SIDE-1;x>=0;x--) {
                for (int y=SIDE-1;y>=0;y--) {
                    for (int z=0;z<SIDE;z++) {
                        for (int [] block : shape) {
                            if ((block[0]==x) && (block[1]==y) && (block[2]==z)) {
                                float tx = 86.60254f * (x-y);
                                float ty = -50.0f    * (x+y) - 100f * z;
                                out.write("<use xlink:href='#block' transform='translate(" + tx + "," + ty + ")'/>\n");
                            }
                        }
                    }
                }
            }
            out.write("</g>\n");
        }

        // Close of definitions and open layout graphic
        out.write("</defs>");
        out.write("<g style='fill:none;stroke:black;'>");

        // Layout blocks
        float x = 0f;
        float y = -200f;
        for (int b=0;b<soln.length;b++) {

            if ((b % 5) == 0) {
                y+=1000;
                x=800f;
            } else {
                x+=800f;
            }

            out.write("<use xlink:href='#cube' x='" + x + "' y='" + y + "'/>\n");
            out.write("<use xlink:href='#block" + b + "' x='" + x + "' y='" + y + "'/>\n");
            out.write("<text x='" + x + "' y='" + (y+200) + "' font-size='50'>" + b + "</text>\n");

        }

        // Close layout graphic and file
        out.write("</g>");
        out.write("</svg>");
        out.close();
        System.out.println("Sample solution output to bedlam.svg");
    }

    public static void main(String[] args) throws Exception {

        // Output SVG representation
        toSVG(SOLN);

        long t = System.currentTimeMillis();
        Bedlam b = new Bedlam();
        System.out.println("Bedlam created in " + (System.currentTimeMillis()-t) + "ms");
        if (true) b.search2();
        System.out.println("Complete " + (System.currentTimeMillis()-t) + "ms");
    }

}
