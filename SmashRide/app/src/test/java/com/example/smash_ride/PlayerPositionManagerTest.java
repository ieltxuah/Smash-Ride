package com.example.smash_ride;

import static org.mockito.Mockito.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PlayerPositionManagerTest {

    @Mock
    private FirebaseDatabase mockDatabase;
    @Mock
    private DatabaseReference mockReference;

    @Before
    public void setup() {
        when(mockDatabase.getReference(anyString())).thenReturn(mockReference);
    }

    @Test
    public void testSavePositionCallsFirebase() {
        try (MockedStatic<FirebaseDatabase> mockedFirebase = mockStatic(FirebaseDatabase.class)) {
            mockedFirebase.when(() -> FirebaseDatabase.getInstance(anyString())).thenReturn(mockDatabase);

            PlayerPositionManager manager = new PlayerPositionManager("test-uid");
            manager.savePosition(1.0f, 2.0f);

            verify(mockReference).updateChildren(any());
        }
    }
}
