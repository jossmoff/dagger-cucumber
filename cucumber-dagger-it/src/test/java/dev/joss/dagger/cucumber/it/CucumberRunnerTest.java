package dev.joss.dagger.cucumber.it;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("dev.joss.dagger.cucumber.it")
public class CucumberRunnerTest {}
