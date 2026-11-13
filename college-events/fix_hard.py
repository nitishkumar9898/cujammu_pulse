import os
import re

base_dir = r'c:\Users\NITISH KUMAR\OneDrive\Desktop\college-events-project\college-events\src\main\resources\templates'

def replace_in_file(rel_path, replacements):
    f_path = os.path.join(base_dir, rel_path)
    if not os.path.exists(f_path): return
    with open(f_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    for old, new in replacements:
        if callable(old):
            content = old(content)
        elif old.startswith('REGEX:'):
            content = re.sub(old[6:], new, content)
        else:
            content = content.replace(old, new)
            
    with open(f_path, 'w', encoding='utf-8') as f:
        f.write(content)

# 1. edit-event.html
replace_in_file(r'organizer\edit-event.html', [
    ('aria-label="Event Title"', 'aria-label="Event Title" title="Event Title" placeholder="Event Title"'),
    ('aria-label="Description"', 'aria-label="Description" title="Description" placeholder="Description"'),
    ('aria-label="Category"', 'aria-label="Category" title="Category" placeholder="Category"'),
    ('aria-label="Venue"', 'aria-label="Venue" title="Venue" placeholder="Venue"'),
    ('aria-label="Event Date and Time"', 'aria-label="Event Date and Time" title="Event Date and Time" placeholder="Event Date and Time"'),
    ('aria-label="Max Seats"', 'aria-label="Max Seats" title="Max Seats" placeholder="Max Seats"'),
    ('aria-label="Fee"', 'aria-label="Fee" title="Fee" placeholder="Fee"'),
    ('aria-label="Contact Email"', 'aria-label="Contact Email" title="Contact Email" placeholder="Contact Email"'),
    ('aria-label="New Banner Image"', 'aria-label="New Banner Image" title="New Banner Image" placeholder="New Banner Image"'),
    ('aria-label="Custom Certificate Template"', 'aria-label="Custom Certificate Template" title="Custom Certificate Template" placeholder="Custom Certificate Template"'),
    ('aria-label="Full Name"', 'aria-label="Full Name" title="Full Name" placeholder="Full Name"'),
    ('aria-label="Short Bio"', 'aria-label="Short Bio" title="Short Bio" placeholder="Short Bio"'),
    ('aria-label="LinkedIn URL"', 'aria-label="LinkedIn URL" title="LinkedIn URL" placeholder="LinkedIn URL"'),
    ('aria-label="Company Name"', 'aria-label="Company Name" title="Company Name" placeholder="Company Name"'),
    ('aria-label="Sponsorship Tier"', 'aria-label="Sponsorship Tier" title="Sponsorship Tier" placeholder="Sponsorship Tier"'),
    ('aria-label="Website URL"', 'aria-label="Website URL" title="Website URL" placeholder="Website URL"'),
])

# 2. registrations.html
replace_in_file(r'organizer\registrations.html', [
    ('<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', '<button type="button" class="btn-close" data-bs-dismiss="modal" title="Close" aria-label="Close"></button>')
])

# 3. scan-qr.html
replace_in_file(r'organizer\scan-qr.html', [
    ('content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no"', 'content="width=device-width,initial-scale=1.0"'),
    ('backdrop-filter: blur(8px);', '-webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px);'),
    ('<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', '<button type="button" class="btn-close" data-bs-dismiss="modal" title="Close" aria-label="Close"></button>'),
    ('aria-label="Status"', 'aria-label="Status" title="Status" placeholder="Status"'),
    ('aria-label="Upload QR"', 'aria-label="Upload QR" title="Upload QR" placeholder="Upload QR"'),
    ('<select name="status" class="form-select mb-3" required>', '<select name="status" class="form-select mb-3" required title="Status" aria-label="Status">'),
    ('<input type="file" id="qr-input-file" accept="image/*" class="d-none">', '<input type="file" id="qr-input-file" accept="image/*" class="d-none" title="Upload QR" aria-label="Upload QR">')
])

# 4. event-detail.html
replace_in_file(r'student\event-detail.html', [
    ('class="card-img-top"', 'class="card-img-top" title="Banner"'),
    ('class="rounded-circle mb-3"', 'class="rounded-circle mb-3" title="Speaker"'),
    ('alt="Banner Image"', 'alt="Banner Image" title="Banner Image"'),
    ('alt="Speaker Image"', 'alt="Speaker Image" title="Speaker Image"')
])

# 5. my-tickets.html
replace_in_file(r'student\my-tickets.html', [
    ('data-bs-toggle="collapse"', 'data-bs-toggle="collapse" title="Toggle"'),
    ('<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', '<button type="button" class="btn-close" data-bs-dismiss="modal" title="Close" aria-label="Close"></button>')
])

# 6. profile.html
replace_in_file(r'student\profile.html', [
    ('aria-label="Name"', 'aria-label="Name" title="Name" placeholder="Name"'),
    ('aria-label="Email"', 'aria-label="Email" title="Email" placeholder="Email"'),
    ('aria-label="Student ID"', 'aria-label="Student ID" title="Student ID" placeholder="Student ID"'),
    ('aria-label="Phone Number"', 'aria-label="Phone Number" title="Phone Number" placeholder="Phone Number"'),
    ('aria-label="Current Password"', 'aria-label="Current Password" title="Current Password" placeholder="Current Password"'),
    ('aria-label="New Password"', 'aria-label="New Password" title="New Password" placeholder="New Password"'),
    ('aria-label="Confirm Password"', 'aria-label="Confirm Password" title="Confirm Password" placeholder="Confirm Password"')
])

print("done hard fixes")
