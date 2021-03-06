/*
 * Copyright 2005 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.geometry;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public strictfp class S2CellIdTest extends GeometryTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(S2CellIdTest.class);

    private static final int MAX_WALK_LEVEL = 8;
    private static final int MAX_EXPAND_LEVEL = 3;

    private S2CellId getCellId(double latDegrees, double lngDegrees) {
        S2CellId id = S2CellId.fromLatLng(S2LatLng.fromDegrees(latDegrees, lngDegrees));
        LOG.info(Long.toString(id.id(), 16));
        return id;
    }

    @Test
    public void testBasic() {
        LOG.info("TestBasic");
        // Check default constructor.
        S2CellId id = S2CellId.none();
        assertEquals(id.id(), 0);
        assertTrue(!id.isValid());

        // Check basic accessor methods.
        id = S2CellId.fromFacePosLevel(3, 0x12345678, S2CellId.MAX_LEVEL - 4);
        assertTrue(id.isValid());
        assertEquals(id.face(), 3);
        assertEquals(id.pos(), 0x12345700);
        assertEquals(id.level(), S2CellId.MAX_LEVEL - 4);
        assertTrue(!id.isLeaf());

        // Check face definitions
        // center face
        assertEquals(getCellId(45, 0).face(), 0);
        assertEquals(getCellId(0, 45).face(), 0);
        assertEquals(getCellId(0, 0).face(), 0); // center
        assertEquals(getCellId(-45, 0).face(), 0);
        assertEquals(getCellId(0, -45).face(), 0);
        // east face
        assertEquals(getCellId(45, 90).face(), 1);
        assertEquals(getCellId(0, 135).face(), 1);
        assertEquals(getCellId(0, 90).face(), 1); // center
        assertEquals(getCellId(-45, 90).face(), 1);
        assertEquals(getCellId(0, 45.1).face(), 1);
        // east-west face
        assertEquals(getCellId(45, -180).face(), 3);
        assertEquals(getCellId(0, 135.1).face(), 3);
        assertEquals(getCellId(0, 180).face(), 3); // center
        assertEquals(getCellId(0, -180).face(), 3); // center
        assertEquals(getCellId(0, -135.1).face(), 3);
        assertEquals(getCellId(-45, 180).face(), 3);
        // west face
        assertEquals(getCellId(45, -90).face(), 4);
        assertEquals(getCellId(0, -135).face(), 4);
        assertEquals(getCellId(0, -90).face(), 4); // center
        assertEquals(getCellId(-45, -90).face(), 4);
        assertEquals(getCellId(0, -45.1).face(), 4);
        // north face
        assertEquals(getCellId(45.1, -180).face(), 2);
        assertEquals(getCellId(67.5, -135).face(), 2);
        assertEquals(getCellId(90, 0).face(), 2); // center
        assertEquals(getCellId(45.1, 180).face(), 2);
        assertEquals(getCellId(67.5, 135).face(), 2);
        // south face
        assertEquals(getCellId(-45.1, 180).face(), 5);
        assertEquals(getCellId(-67.5, 135).face(), 5);
        assertEquals(getCellId(-90, 0).face(), 5); // center
        assertEquals(getCellId(-45.1, -180).face(), 5);
        assertEquals(getCellId(-67.5, -135).face(), 5);

        // Check parent/child relationships.
        assertEquals(id.childBegin(id.level() + 2).pos(), 0x12345610);
        assertEquals(id.childBegin().pos(), 0x12345640);
        assertEquals(id.parent().pos(), 0x12345400);
        assertEquals(id.parent(id.level() - 2).pos(), 0x12345000);

        // Check ordering of children relative to parents.
        assertTrue(id.childBegin().lessThan(id));
        assertTrue(id.childEnd().greaterThan(id));
        assertEquals(id.childBegin().next().next().next().next(), id.childEnd());
        assertEquals(id.childBegin(S2CellId.MAX_LEVEL), id.rangeMin());
        assertEquals(id.childEnd(S2CellId.MAX_LEVEL), id.rangeMax().next());

        // Check wrapping from beginning of Hilbert curve to end and vice versa.
        assertEquals(S2CellId.begin(0).prevWrap(), S2CellId.end(0).prev());

        assertEquals(S2CellId.begin(S2CellId.MAX_LEVEL).prevWrap(),
                S2CellId.fromFacePosLevel(5, ~0L >>> S2CellId.FACE_BITS, S2CellId.MAX_LEVEL));

        assertEquals(S2CellId.end(4).prev().nextWrap(), S2CellId.begin(4));
        assertEquals(S2CellId.end(S2CellId.MAX_LEVEL).prev().nextWrap(), S2CellId.fromFacePosLevel(0, 0, S2CellId.MAX_LEVEL));

        // Check that cells are represented by the position of their center along the Hilbert curve.
        assertEquals(id.rangeMin().id() + id.rangeMax().id(), 2 * id.id());
    }

    @Test
    public void testInverses() {
        LOG.info("TestInverses");
        // Check the conversion of random leaf cells to S2LatLngs and back.
        for (int i = 0; i < 200000; ++i) {
            S2CellId id = getRandomCellId(S2CellId.MAX_LEVEL);
            assertTrue(id.isLeaf() && id.level() == S2CellId.MAX_LEVEL);
            S2LatLng center = id.toLatLng();
            assertEquals(S2CellId.fromLatLng(center).id(), id.id());
        }
    }

    @Test
    public void testToToken() {
        assertEquals("000000000000010a", new S2CellId(266).toToken());
        assertEquals("80855c", new S2CellId(-9185834709882503168L).toToken());
    }

    @Test
    public void testTokens() {
        LOG.info("TestTokens");

        // Test random cell ids at all levels.
        for (int i = 0; i < 10000; ++i) {
            S2CellId id = getRandomCellId();
            if (!id.isValid()) {
                continue;
            }
            String token = id.toToken();
            assertTrue(token.length() <= 16);
            assertEquals(S2CellId.fromToken(token), id);
        }
        // Check that invalid cell ids can be encoded.
        String token = S2CellId.none().toToken();
        assertEquals(S2CellId.fromToken(token), S2CellId.none());
    }

    private void expandCell(S2CellId parent, ArrayList<S2CellId> cells, Map<S2CellId, S2CellId> parentMap) {
        cells.add(parent);
        if (parent.level() == MAX_EXPAND_LEVEL) {
            return;
        }
        MutableInteger i = new MutableInteger(0);
        MutableInteger j = new MutableInteger(0);
        MutableInteger orientation = new MutableInteger(0);
        int face = parent.toFaceIJOrientation(i, j, orientation);
        assertEquals(face, parent.face());

        int pos = 0;
        for (S2CellId child = parent.childBegin(); !child.equals(parent.childEnd());
             child = child.next()) {
            // Do some basic checks on the children
            assertEquals(child.level(), parent.level() + 1);
            assertTrue(!child.isLeaf());
            MutableInteger childOrientation = new MutableInteger(0);
            assertEquals(child.toFaceIJOrientation(i, j, childOrientation), face);
            assertEquals(childOrientation.intValue(), orientation.intValue() ^ S2.posToOrientation(pos));
            parentMap.put(child, parent);
            expandCell(child, cells, parentMap);
            ++pos;
        }
    }

    @Test
    public void testContainment() {
        LOG.info("TestContainment");
        Map<S2CellId, S2CellId> parentMap = new HashMap<>();
        ArrayList<S2CellId> cells = new ArrayList<>();
        for (int face = 0; face < 6; ++face) {
            expandCell(S2CellId.fromFacePosLevel(face, 0, 0), cells, parentMap);
        }
        for (int i = 0; i < cells.size(); ++i) {
            for (S2CellId cell : cells) {
                boolean contained = true;
                for (S2CellId id = cell; id != cells.get(i); id = parentMap.get(id)) {
                    if (!parentMap.containsKey(id)) {
                        contained = false;
                        break;
                    }
                }
                assertEquals(cells.get(i).contains(cell), contained);
                assertEquals(cell.greaterOrEquals(cells.get(i).rangeMin())
                        && cell.lessOrEquals(cells.get(i).rangeMax()), contained);
                assertEquals(cells.get(i).intersects(cell),
                        cells.get(i).contains(cell) || cell.contains(cells.get(i)));
            }
        }
    }

    @Test
    public void testContinuity() {
        LOG.info("TestContinuity");
        // Make sure that sequentially increasing cell ids form a continuous
        // path over the surface of the sphere, i.e. there are no
        // discontinuous jumps from one region to another.
        double maxDist = S2Projections.MAX_EDGE.getValue(MAX_WALK_LEVEL);
        S2CellId end = S2CellId.end(MAX_WALK_LEVEL);
        S2CellId id = S2CellId.begin(MAX_WALK_LEVEL);
        for (; !id.equals(end); id = id.next()) {
            assertTrue(id.toPointRaw().angle(id.nextWrap().toPointRaw()) <= maxDist);
            // Check that the ToPointRaw() returns the center of each cell in (s,t) coordinates.
            S2Point p = id.toPointRaw();
            int face = S2Projections.xyzToFace(p);
            R2Vector uv = S2Projections.validFaceXyzToUv(face, p);
            assertDoubleNear(Math.IEEEremainder(S2Projections.uvToST(uv.x()), 1.0 / (1 << MAX_WALK_LEVEL)), 0);
            assertDoubleNear(Math.IEEEremainder(S2Projections.uvToST(uv.y()), 1.0 / (1 << MAX_WALK_LEVEL)), 0);
        }
    }

    @Test
    public void testCoverage() {
        LOG.info("TestCoverage");
        // Make sure that random points on the sphere can be represented to the
        // expected level of accuracy, which in the worst case is sqrt(2/3) times
        // the maximum arc length between the points on the sphere associated with
        // adjacent values of "i" or "j". (It is sqrt(2/3) rather than 1/2 because
        // the cells at the corners of each face are stretched -- they have 60 and
        // 120 degree angles.)
        double maxDist = 0.5 * S2Projections.MAX_DIAG.getValue(S2CellId.MAX_LEVEL);
        for (int i = 0; i < 1000000; ++i) {
            // randomPoint();
            S2Point p = new S2Point(0.37861576725894824, 0.2772406863275093, 0.8830558887338725);
            S2Point q = S2CellId.fromPoint(p).toPointRaw();
            assertTrue(p.angle(q) <= maxDist);
        }
    }

    private void allNeighbors(S2CellId id, int level) {
        assertTrue(level >= id.level() && level < S2CellId.MAX_LEVEL);
        // We compute GetAllNeighbors, and then add in all the children of "id"
        // at the given level. We then compare this against the result of finding
        // all the vertex neighbors of all the vertices of children of "id" at the
        // given level. These should give the same result.
        ArrayList<S2CellId> all = new ArrayList<>();
        ArrayList<S2CellId> expected = new ArrayList<>();
        id.getAllNeighbors(level, all);
        S2CellId end = id.childEnd(level + 1);
        for (S2CellId c = id.childBegin(level + 1); !c.equals(end); c = c.next()) {
            all.add(c.parent());
            c.getVertexNeighbors(level, expected);
        }
        // Sort the results and eliminate duplicates.
        Collections.sort(all);
        Collections.sort(expected);
        Set<S2CellId> allSet = new HashSet<>(all);
        Set<S2CellId> expectedSet = new HashSet<>(expected);
        assertTrue(allSet.equals(expectedSet));
    }

    @Test
    public void testNeighbors() {
        LOG.info("TestNeighbors");

        // Check the edge neighbors of face 1.
        final int outFaces[] = {5, 3, 2, 0};
        S2CellId faceNbrs[] = new S2CellId[4];
        S2CellId.fromFacePosLevel(1, 0, 0).getEdgeNeighbors(faceNbrs);
        for (int i = 0; i < 4; ++i) {
            assertTrue(faceNbrs[i].isFace());
            assertEquals(faceNbrs[i].face(), outFaces[i]);
        }

        // Check the vertex neighbors of the center of face 2 at level 5.
        ArrayList<S2CellId> nbrs = new ArrayList<>();
        S2CellId.fromPoint(new S2Point(0, 0, 1)).getVertexNeighbors(5, nbrs);
        Collections.sort(nbrs);
        for (int i = 0; i < 4; ++i) {
            assertEquals(nbrs.get(i), S2CellId.fromFaceIJ(
                    2, (1 << 29) - (i < 2 ? 1 : 0), (1 << 29) - ((i == 0 || i == 3) ? 1 : 0)).parent(5));
        }
        nbrs.clear();

        // Check the vertex neighbors of the corner of faces 0, 4, and 5.
        S2CellId id = S2CellId.fromFacePosLevel(0, 0, S2CellId.MAX_LEVEL);
        id.getVertexNeighbors(0, nbrs);
        Collections.sort(nbrs);
        assertEquals(nbrs.size(), 3);
        assertEquals(nbrs.get(0), S2CellId.fromFacePosLevel(0, 0, 0));
        assertEquals(nbrs.get(1), S2CellId.fromFacePosLevel(4, 0, 0));
        assertEquals(nbrs.get(2), S2CellId.fromFacePosLevel(5, 0, 0));

        // Check that GetAllNeighbors produces results that are consistent
        // with GetVertexNeighbors for a bunch of random cells.
        for (int i = 0; i < 1000; ++i) {
            S2CellId id1 = getRandomCellId();
            if (id1.isLeaf()) {
                id1 = id1.parent();
            }

            // TestAllNeighbors computes approximately 2**(2*(diff+1)) cell id1s,
            // so it's not reasonable to use large values of "diff".
            int maxDiff = Math.min(6, S2CellId.MAX_LEVEL - id1.level() - 1);
            int level = id1.level() + random(maxDiff);
            allNeighbors(id1, level);
        }
    }
}
