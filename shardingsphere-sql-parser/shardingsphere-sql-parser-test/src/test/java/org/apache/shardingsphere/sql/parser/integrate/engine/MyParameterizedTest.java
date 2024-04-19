package org.apache.shardingsphere.sql.parser.integrate.engine;

import lombok.AllArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@AllArgsConstructor
public class MyParameterizedTest {
    int a; int b;int expected;
    @Parameterized.Parameters(name = "{0} + {1} = {2}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            {1, 2, 3},
            {4, 5, 9},
            {6, 7, 13},
        });
    }

    @Test
    public void testAddition() {
        int result = a + b;
        assertEquals(expected, result);
    }
}
