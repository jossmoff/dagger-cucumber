package dev.joss.dagger.cucumber;

import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.Glue;
import io.cucumber.core.backend.Snippet;

import java.net.URI;
import java.util.List;

// TODO: Remove suppression once implemented
@SuppressWarnings("StrictUnusedVariable")
public class DaggerBackend implements Backend {

    @Override
    public void loadGlue(Glue glue, List<URI> list) {

    }

    @Override
    public void buildWorld() {

    }

    @Override
    public void disposeWorld() {

    }

    @Override
    public Snippet getSnippet() {
        return null;
    }
}
