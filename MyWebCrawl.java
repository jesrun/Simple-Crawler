// Name: Jessica Runandy
// Date: October 9, 2020
// Program 1: Simple Crawler
// Description: This program uses HTTP to GET and crawl parts of the HTML
// based Web. It will download and parse through the HTML of the URL given and
// hop a number of times based on the number of hops. It will print the URL visited
// and print HTML of the last URL visited.

// References:
// 1. https://stackoverflow.com/questions/5120171/extract-links-from-a-web-page
// 2. https://stackoverflow.com/questions/163360/regular-expression-to-match-urls-in-java


import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyWebCrawl {

    private static List<String> pagesVisited = new ArrayList<>(); // Pages visited
    private static int numberOfHops = 0; // Number of hops inputted by the user
    private static int printHops = 0; // Number of hops to be printed
    private static String currentURL = ""; // The current URL
    private static boolean continueLoop = false; // Flag for the while loop

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length != 2) {
            System.out.println("ERROR: Invalid number of arguments. " +
                    "Please enter two arguments (URL and number of hops)");
            return;
        }

         String consoleStartingURL = args[0];
         String hops = args[1];

        // If the string hop inputted by the user is not a number
        try {
            // Converts the string hop from console to an integer
            numberOfHops = Integer.parseInt(hops);
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid number of hops. Please enter a positive value.");
            return;
        }

        // Checks for valid URL format (should start with either https:// or http://)
        String regexURL = "(https|http)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern startingURL = Pattern.compile(regexURL, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcherURL = startingURL.matcher(consoleStartingURL);
        if (!matcherURL.find()) {
            System.out.println("ERROR: Invalid URL format. Please enter an URL starting with " +
                    "either https:// or http://");
            return;
        }

        // Prints error message if number of hops is negative
        if (numberOfHops <= 0) {
            System.out.println("ERROR: Invalid number of hops. Please enter a positive value.");
        } else {
            System.out.println("Starting URL: " + consoleStartingURL);
            System.out.println("Starting number of hops: " + numberOfHops);

            // Initializes the variables
            currentURL = consoleStartingURL;
            HttpClient client = null;
            HttpRequest request = null;
            HttpResponse<String> response = null;
            int responseCode;
            List<String> links; // Stores all the URLs on the page
            String previousURL = currentURL; // Stores the previous link if need to backtrack
                                             // (for return codes in the 400s and 500s)
            boolean extractURL = true;  // Keep track if the HTML was able to be extracted

            boolean breakValid = false;

            updatePagesVisited(currentURL);

            while (numberOfHops > 0) {
                try {
                    // Creates a new HTTP client, automatically redirects for requests with return codes
                    // in the 300s
                    client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                            .followRedirects(HttpClient.Redirect.ALWAYS).build();

                    // Sends a request to the web page inputted by the user using GET
                    request = HttpRequest.newBuilder().uri(URI.create(currentURL)).GET().build();

                    // Downloads the HTML from the website and stores it in response
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());

                } catch (ConnectException e) { // Response 400-499: Client errors, incorrect user input
                    boolean breakOut = catch400Error(previousURL, breakValid);
                    if (breakOut) { // break when unable to redirect
                        break;
                    }
                    if (continueLoop) { // currentURL updated, continue the while loop
                        continue;
                    }
                }

                responseCode = response.statusCode(); // Saves the response code

                // For response in the 400s that's not caught by the ConnectException
                if (responseCode > 399 && responseCode < 500) {
                    boolean breakOut = catch400Error(previousURL, breakValid);
                    if (breakOut) { // break when unable to redirect
                        break;
                    }
                    if (continueLoop) { // currentURL updated, continue the while loop
                        continue;
                    }
                }

                // Response 500-599: Server errors
                if (responseCode > 499 && responseCode < 600) {
                    // Loop to wait for a couple of seconds and retry
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(3000); // Wait for 3 seconds
                        // Retry to connect to the server
                        response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        responseCode = response.statusCode();

                        if (responseCode < 500) { // If response code not in the 500s, means retry succeeded
                            break; // Breaks out of the for loop
                        }
                    }

                    // If response code is in the 500s after retrying, go to previous page and go
                    // to another link
                    if (responseCode > 499 && responseCode < 600) {
                        // If there's at least 2 unique URLs in the pageVisited
                        if (pagesVisited.size() > 2) {
                            currentURL = previousURL; // Get the last visited link
                            printHops++; // Update the number of hops printed
                            numberOfHops--; // Update the number of hops
                            if (numberOfHops == 0) {
                                System.out.println("Hop " + printHops + " ERROR: Server error, unable to redirect.");
                                break;
                            } else {
                                System.out.println("Hop " + printHops + " ERROR: Server error, backtracking to " +
                                        "previous website to find the next link.");
                            }
                            continue; // currentURL updated, continue the while loop
                        } else { //  No previous link to redirect to (pagesVisited is empty)
                            System.out.println("Hop 1 ERROR: Server error, unable to redirect.");
                            break; // Breaks out of the while loop
                        }
                    }
                }

                String html = response.body(); // Stores the HTML code as a string
                links = validURL(html); // links contains the list of URLs in the page

                boolean linkNotVisited = false;
                int linksIndex = 0; // Keeps track of the index of the list of links
                boolean currentURLUpdated = false;

                // Loop to make sure that the next link that's going to be visited has not been visited
                // (not in the pagesVisited list)
                while (linksIndex < links.size() && !linkNotVisited) {
                    String currentLinkInLinksList = links.get(linksIndex);

                    // Loop through pagesVisited to check if the current page on the link list
                    // has been visited
                    for (String s : pagesVisited) {
                        // If the currentLinkInLinkList has been visited, break and continue to
                        // the next page in the links list
                        if (s.equalsIgnoreCase(currentLinkInLinksList)) {
                            linkNotVisited = false;
                            break;
                        } else { // currentLinkInLinksList has not been visited
                            // No break here because want to continue the for loop to check for all the
                            // links in pagesVisited
                            linkNotVisited = true;
                        }
                    }

                    // Update the current URL to the next URL that has not been
                    // visited in the links list
                    if (linkNotVisited) {
                        // Store the previous URL for backtracking if needed
                        previousURL = currentURL;
                        currentURL = currentLinkInLinksList;
                        currentURLUpdated = true;
                        break; // break if the currentLinkInLinkList has not been visited
                    }
                    linksIndex++; // Update to go to the next link in the links list
                }

                // If the currentURL has been updated (was able to hop to another link),
                // then add it to the pagesVisited list
                if (currentURLUpdated) {
                    updatePagesVisited(currentURL); // Update pagesVisited
                }

                // If there's no links on the page or the page does not have any accessible
                // embedded references, stop and print the result.
                if (links.isEmpty()) {
                    System.out.println("Unable to find the <a href > reference to other absolute URLs.");
                    extractURL = false;
                    numberOfHops = 0; // Update to 0 to break out of the loop
                    break;
                }

                printHops++;
                System.out.print("Hop " + printHops + " URL: ");
                System.out.println(currentURL); // Prints the current URL
                numberOfHops--;
            }
            printHtml(numberOfHops, printHops, extractURL, response); // Prints the HTML
        }
    }

    // Takes in html as a string and uses regex to extract the http and https URLs.
    // Returns a list of strings containing the URLs.
    private static List<String> validURL(String html) {
        // Regex to extract the <a href> statements from the html
        String regex = "<a[^>]href=\"(https|http)://([^\"'>]+)[\"']?[^>]*>(.+?)</a>";
        Pattern hrefPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = hrefPattern.matcher(html);
        ArrayList<String> htmlReference = new ArrayList<>(); // stores the html references
        while (matcher.find()) {
            htmlReference.add(matcher.group()); // contains the <a href >
        }

        List<String> links = new ArrayList<>(); // stores only the URLs
        // Regex to extract the http and https URLs from the <a href> statements
        String httpRegex = "(https|http)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern pattern = Pattern.compile(httpRegex); // Compiling regex
        for (String s : htmlReference) { // Loop to extract only the URLs
            Matcher httpMatcher = pattern.matcher(s); // Checks if the URL matches the regex pattern
            while (httpMatcher.find()) {
                links.add(httpMatcher.group()); // Adds only the URL to the list of links
            }
        }
        return links;
    }

    // Takes in the current URL (currentURL) as a string and update the pagesVisited list
    // so that an URL with a trailing "/" is seen as the same as one without a trailing "/".
    private static void updatePagesVisited(String currentURL) {
        pagesVisited.add(currentURL); // Update pagesVisited list to add the current URL
        String otherURL;
        if (currentURL.endsWith("/")) {
            otherURL = currentURL.substring(0, currentURL.length() - 1);
        } else {
            otherURL = currentURL + "/";
        }
        pagesVisited.add(otherURL);
    }

    // Takes in the number of hops inputted by the user (numberOfHops), printHops (number of hops
    // the program is currently on), extractURL (if the URL was extracted successfully from the HTML),
    // and the response (http response) and prints the number of hops completed and the HTML of the
    // last website completed.
    private static void printHtml(int numberOfHops, int printHops, boolean extractURL,
                                  HttpResponse<String> response) {
        // Prints the HTML code when number of hops reaches 0 and if able to extract the URLs
        // if (numberOfHops == 0 && extractURL) {

        // Prints the HTML code when number of hops reaches 0 and if there is a previous page
        if (numberOfHops == 0 && pagesVisited.size() > 2 && response != null) {
            if (printHops == 1) {
                System.out.println(printHops + " hop has been completed");
            } else {
                System.out.println(printHops + " hops has been completed");
            }
            System.out.println("Printing the HTML from the last website visited:");
            System.out.println(response.body());
        } else if (numberOfHops == 0 && !extractURL && pagesVisited.size() < 3) {
            // Unable to extract URL and there's only 1 unique link in the pagesVisited list
            System.out.println(printHops + " hop has been completed");
            System.out.println("Printing the HTML from the last website visited:");
            System.out.println(response.body());
        }
    }

    // Takes in the previous URL (previousURL) and breakValid to determine the error
    // messages printed depending on the number of hops left and if the program is able to
    // redirect for response code 400s.
    // Returns breakValid to determine if there should be a break in the while loop.
    private static boolean catch400Error(String previousURL, boolean breakValid) {
        // Cannot print, go to previous page and go to another link
        // If there is a previous link (pagesVisited list at least 1 link)
        if (pagesVisited.size() > 2) {
            currentURL = previousURL; // Get the last visited link
            printHops++; // Update the number of hops printed
            numberOfHops--; // Update the number of hops
            if (numberOfHops == 0) {
                System.out.println("Hop " + printHops + " ERROR: Invalid link, unable to redirect.");
                breakValid = true;
            } else {
                System.out.println("Hop " + printHops + " ERROR: Invalid link, backtracking " +
                        "to previous website to find the next link.");
            }
            continueLoop = true; // currentURL updated, continue the while loop
        } else { //  No previous link to redirect to (pagesVisited is empty)
            System.out.println("Hop 1 ERROR: Invalid link, unable to redirect.");
            breakValid = true;
        }
        return breakValid;
    }
}