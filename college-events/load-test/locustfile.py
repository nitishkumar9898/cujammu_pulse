from locust import HttpUser, task, between

class CollegeEventUser(HttpUser):
    # Wait time between tasks (simulates real user reading the page)
    wait_time = between(1, 3)

    @task(3)
    def view_events_list(self):
        # Hits the cached /search or /events endpoints
        self.client.get("/")

    @task(1)
    def search_events(self):
        self.client.get("/?keyword=tech")

    @task(1)
    def view_event_details(self):
        # We assume event ID 1 exists, ideally we'd scrape the homepage for IDs
        self.client.get("/events/1", name="/events/[id]")

    def on_start(self):
        """
        Setup code when a simulated user starts.
        You can put login logic here if testing authenticated routes.
        """
        pass
