import re
import os

base_dir = r'c:\Users\NITISH KUMAR\OneDrive\Desktop\college-events-project\college-events\src\main\resources\templates'

# registrations.html
f1 = os.path.join(base_dir, r'organizer\registrations.html')
if os.path.exists(f1):
    with open(f1, 'r', encoding='utf-8') as f: content = f.read()
    content = content.replace('<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" title="Close modal"></button>')
    with open(f1, 'w', encoding='utf-8') as f: f.write(content)

# scan-qr.html
f2 = os.path.join(base_dir, r'organizer\scan-qr.html')
if os.path.exists(f2):
    with open(f2, 'r', encoding='utf-8') as f: content = f.read()
    content = content.replace('content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0"', 'content="width=device-width, initial-scale=1.0"')
    content = content.replace('backdrop-filter: blur(8px);', '-webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px);')
    content = content.replace('<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" title="Close modal"></button>')
    content = content.replace('<select name="status" class="form-select mb-3" required>', '<select name="status" class="form-select mb-3" required aria-label="Status">')
    content = content.replace('<input type="file" id="qr-input-file" accept="image/*" class="d-none">', '<input type="file" id="qr-input-file" accept="image/*" class="d-none" aria-label="Upload QR">')
    with open(f2, 'w', encoding='utf-8') as f: f.write(content)

# event-detail.html
f3 = os.path.join(base_dir, r'student\event-detail.html')
if os.path.exists(f3):
    with open(f3, 'r', encoding='utf-8') as f: content = f.read()
    content = content.replace('<img th:if="${event.bannerImage != null}" th:src="@{\'/uploads/\' + ${event.bannerImage}}" class="card-img-top"', '<img th:if="${event.bannerImage != null}" th:src="@{\'/uploads/\' + ${event.bannerImage}}" class="card-img-top" alt="Banner Image"')
    content = content.replace('<img th:if="${spk.imageUrl != null}" th:src="${spk.imageUrl}" class="rounded-circle mb-3"', '<img th:if="${spk.imageUrl != null}" th:src="${spk.imageUrl}" class="rounded-circle mb-3" alt="Speaker Image"')
    with open(f3, 'w', encoding='utf-8') as f: f.write(content)

# my-tickets.html
f4 = os.path.join(base_dir, r'student\my-tickets.html')
if os.path.exists(f4):
    with open(f4, 'r', encoding='utf-8') as f: content = f.read()
    content = content.replace('<button class="btn btn-primary d-md-none" type="button" data-bs-toggle="collapse"', '<button class="btn btn-primary d-md-none" type="button" data-bs-toggle="collapse" aria-label="Toggle Filters" title="Toggle Filters"')
    content = content.replace('<button type="button" class="btn-close" data-bs-dismiss="modal"></button>', '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" title="Close modal"></button>')
    with open(f4, 'w', encoding='utf-8') as f: f.write(content)

# profile.html
f5 = os.path.join(base_dir, r'student\profile.html')
if os.path.exists(f5):
    with open(f5, 'r', encoding='utf-8') as f: content = f.read()
    content = content.replace('<input type="text" th:field="*{name}" class="form-control"', '<input type="text" th:field="*{name}" class="form-control" aria-label="Name"')
    content = content.replace('<input type="email" th:field="*{email}" class="form-control"', '<input type="email" th:field="*{email}" class="form-control" aria-label="Email"')
    content = content.replace('<input type="text" th:field="*{studentId}" class="form-control"', '<input type="text" th:field="*{studentId}" class="form-control" aria-label="Student ID"')
    content = content.replace('<input type="text" th:field="*{phone}" class="form-control"', '<input type="text" th:field="*{phone}" class="form-control" aria-label="Phone Number"')
    content = content.replace('<input type="password" name="currentPassword" class="form-control"', '<input type="password" name="currentPassword" class="form-control" aria-label="Current Password"')
    content = content.replace('<input type="password" name="newPassword" class="form-control"', '<input type="password" name="newPassword" class="form-control" aria-label="New Password"')
    content = content.replace('<input type="password" name="confirmPassword" class="form-control"', '<input type="password" name="confirmPassword" class="form-control" aria-label="Confirm Password"')
    with open(f5, 'w', encoding='utf-8') as f: f.write(content)

print("done html fixes")

