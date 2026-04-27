import re

file_path = r'c:\Users\NITISH KUMAR\OneDrive\Desktop\college-events-project\college-events\src\main\resources\templates\organizer\edit-event.html'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

replacements = [
    (r'<input type="text" th:field="\*{title}" class="form-control form-control-lg"', r'<input type="text" th:field="*{title}" class="form-control form-control-lg" aria-label="Event Title"'),
    (r'<textarea th:field="\*{description}" class="form-control" rows="5"', r'<textarea th:field="*{description}" class="form-control" rows="5" aria-label="Description"'),
    (r'<select th:field="\*{categoryId}" class="form-select" required>', r'<select th:field="*{categoryId}" class="form-select" required aria-label="Category">'),
    (r'<input type="text" th:field="\*{venue}" class="form-control" required/>', r'<input type="text" th:field="*{venue}" class="form-control" required aria-label="Venue"/>'),
    (r'<input type="datetime-local" th:field="\*{eventDate}" class="form-control" required/>', r'<input type="datetime-local" th:field="*{eventDate}" class="form-control" required aria-label="Event Date and Time"/>'),
    (r'<input type="number" th:field="\*{maxSeats}" class="form-control" min="1" required/>', r'<input type="number" th:field="*{maxSeats}" class="form-control" min="1" required aria-label="Max Seats"/>'),
    (r'<input type="number" th:field="\*{fees}" class="form-control" min="0" step="0\.01"/>', r'<input type="number" th:field="*{fees}" class="form-control" min="0" step="0.01" aria-label="Fee"/>'),
    (r'<input type="email" th:field="\*{contactEmail}" class="form-control"/>', r'<input type="email" th:field="*{contactEmail}" class="form-control" aria-label="Contact Email"/>'),
    (r'<input type="file" th:field="\*{bannerImage}" class="form-control"', r'<input type="file" th:field="*{bannerImage}" class="form-control" aria-label="New Banner Image"'),
    (r'<input type="file" th:field="\*{certificateTemplate}" class="form-control"', r'<input type="file" th:field="*{certificateTemplate}" class="form-control" aria-label="Custom Certificate Template"'),
    (r'<input type="text" name="name" class="form-control" required/>', r'<input type="text" name="name" class="form-control" required aria-label="Full Name"/>'),
    (r'<textarea name="bio" class="form-control" rows="2" maxlength="1000" required></textarea>', r'<textarea name="bio" class="form-control" rows="2" maxlength="1000" required aria-label="Short Bio"></textarea>'),
    (r'<input type="url" name="linkedInUrl" class="form-control"/>', r'<input type="url" name="linkedInUrl" class="form-control" aria-label="LinkedIn URL"/>'),
    (r'<input type="text" name="companyName" class="form-control" required/>', r'<input type="text" name="companyName" class="form-control" required aria-label="Company Name"/>'),
    (r'<select name="tier" class="form-select" required>', r'<select name="tier" class="form-select" required aria-label="Sponsorship Tier">'),
    (r'<input type="url" name="websiteUrl" class="form-control"/>', r'<input type="url" name="websiteUrl" class="form-control" aria-label="Website URL"/>'),
    (r'<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', r'<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" title="Close modal"></button>')
]

for old, new in replacements:
    content = re.sub(old, new, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("done")
