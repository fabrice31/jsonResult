package jenkins.plugins.jsonResult;

import hudson.Extension;
import hudson.model.*;
import hudson.tasks.junit.*;
import hudson.Util;
import hudson.views.ListViewColumn;
import hudson.model.Descriptor.FormException;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.FormFieldValidator;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import javax.servlet.ServletException;
import java.math.BigDecimal;
import java.io.IOException;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.*;
import org.apache.commons.collections.CollectionUtils;

public class JsonResultView extends ListView {
    private final int millisecondsInAMinute = 60000;
    private final double minutesInAnHour = 60.0;

    @DataBoundConstructor
    public JsonResultView(String name) {
        super(name);
    }

  @Extension
  public static final class JsonResultViewDescriptor extends ViewDescriptor {

    @Override
    public String getDisplayName() {
      return "jsonResult";
    }

  }

	public String debug;
	public String title;
	public String regexp;
	private String[][] resultList;


	public String getJsonResult() {
		return getResults();
	}
	
	
    @Override
    protected void submit(StaplerRequest req) throws ServletException,
          Descriptor.FormException, IOException {
		super.submit(req);

		this.regexp = req.getParameter("regexp");
	}


	public String getResults() {
		String pattern = this.regexp;
		String json = "{ \"tests\": [";

		// listing all jobs
		Hudson hudson = Hudson.getInstance();
        List<Job> jobs = hudson.getAllItems(Job.class);
		for(Job job:jobs) {
			// filtering by regexp
			if (job.getName().toString().matches(pattern)) {
				// Name of the job
				json += "{ \"name\": \"" + job.getName().toString() + "\",";
				
				// status and count
				if (job.getLastBuild() != null) {
					if (job.getLastBuild().isBuilding()) {
						json += "\"status\": \"pending\",";

						// time pending
						int currentDuration = (int)(System.currentTimeMillis() - job.getLastBuild().getTimeInMillis());
						json += "\"pending\": " + String.valueOf(currentDuration  / 1000) +",";
						
						// total time
						json += "\"duration\": " + String.valueOf(job.getLastBuild().getEstimatedDuration() / 1000);
						// last item, no comma
						
					} else {
						// counting
						String logFile = job.getLastBuild().getLogFile().toString();
						int nbScenario = getCucumberLogResult(logFile, "all");
						int nbScenarioFailed = getCucumberLogResult(logFile, "failed");

						if (jobIsFailed(job)) {
							json += "\"status\": \"failed\",";
							json += "\"nb_failed\": " + nbScenarioFailed +",";
						} else {
							json += "\"status\": \"passed\",";
						}
						json += "\"nb_tests\": " + nbScenario +",";
						
						// total time
						json += "\"duration\": " + String.valueOf(job.getLastBuild().getDuration() / 1000);
						// last item, no comma
					}
				} else {
					// job don't have status
					json += "\"status\": null";
				}
				
				// end json element
				json += "},";
			}
		}
		
		// delete last comma
		json = json.substring(0, json.length()-1);
		
		// end json
		json += "]}";
		return json;
	}


	private Boolean jobIsFailed (Job job) {
		return !(
			job.getLastBuild().getBuildStatusSummary().message.toString().equalsIgnoreCase("stable")
			|| job.getLastBuild().getBuildStatusSummary().message.toString().equalsIgnoreCase("back to normal")
		);
	}

	
	private int getCucumberLogResult(String fileName, String which)
	{
		Pattern pattern;
		Pattern failed;
		int result;

		// regexp for cucumber result
		// 18 scenarios (2 failed, 16 passed)
		pattern = Pattern.compile("([0-9]+) scenarios? \\((.*)\\)");
		failed = Pattern.compile("([0-9]+) failed.*");
		result = parseLog(fileName, which, pattern, failed);
		if (result > 0) {
			return result;
		}

		// regexp for Unitest result
		// [exec] Tests: 661, Assertions: 2524, Failures: 1, Skipped: 4.
		pattern = Pattern.compile("\\(([0-9]+) tests");
		failed = Pattern.compile("Failures: ([0-9]+)");
		result = parseLog(fileName, which, pattern, failed);
		if (result > 0) {
			return result;
		}

		// other pattern for unitest result
		// OK (43 tests, 87 assertions)
		pattern = Pattern.compile("Tests: ([0-9]+), Assertions: [0-9]+(.*)");
		result = parseLog(fileName, which, pattern, failed);
		if (result > 0) {
			return result;
		}

		// no result found, send 0
		return 0;
	}

	private static int parseLog (String fileName, String which, Pattern pattern, Pattern failed) {
		int results = 0;
		try{
			// Open the log file
			FileInputStream fstream = new FileInputStream(fileName);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   {
				Matcher m = pattern.matcher(strLine);
				if (m.find()) {
					if (which == "failed") {
						Matcher f = failed.matcher(m.group(2));
						if (f.find()) {
							results += Integer.parseInt(f.group(1));
						}
					}
					else if (which == "all") {
						results += Integer.parseInt(m.group(1));
					}
				}
			}
			//Close the input stream
			in.close();
		}catch (Exception e){//Catch exception if any
			// no log file, return 0
			return 0;
		}
		return results;
	}
}
