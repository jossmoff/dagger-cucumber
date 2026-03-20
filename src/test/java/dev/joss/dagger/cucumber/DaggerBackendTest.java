package dev.joss.dagger.cucumber;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DaggerBackendTest {

    @InjectMocks DaggerBackend daggerBackend;

    @Test
    void correctlyInstantiatesDaggerBackend() {
        assertThat(daggerBackend).isNotNull();
    }
}