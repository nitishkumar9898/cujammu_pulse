'use strict';
(() => {
  const base = '/api/v1/cujammu/alumni';
  const notice = document.getElementById('notice');
  const csrf = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
  let requestKey = crypto.randomUUID();
  function announce(message) { notice.textContent = message; notice.hidden = false; }
  async function api(path, method = 'GET', body) {
    const response = await fetch(base + path, { method, credentials: 'same-origin', cache: 'no-store',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json', [csrfHeader]: csrf },
      body: body === undefined ? undefined : JSON.stringify(body) });
    if (response.redirected || response.status === 401) throw new Error('Your session expired. Sign in again to continue.');
    if (!response.ok) throw new Error(({403:'Permission denied. Verification or an authorized role is required.',
      404:'This module or record is unavailable.',409:'This record changed, or verified facts need review. Reload before saving.',
      400:'Check the fields. Use HTTPS links, a LinkedIn profile URL or a valid job email.'})[response.status] || 'Unable to complete the request. Try again.');
    const text = await response.text(); return text ? JSON.parse(text) : null;
  }
  function node(tag, text, className) { const e = document.createElement(tag); e.textContent = text || ''; if(className) e.className=className; return e; }
  function link(href, label) { const a=node('a',label); a.href=href; a.target='_blank'; a.rel='noopener noreferrer'; return a; }
  function button(label, action) { const b=node('button',label); b.type='button'; b.addEventListener('click',() => run(b,action)); return b; }
  async function run(control, action) { control.disabled = true; try { await action(); } catch(e) { announce(e.message); } finally { control.disabled = false; } }
  function pagination(id, page, loader) { const target=document.getElementById(id); target.replaceChildren();
    if(page.page>0) target.append(button('Previous',()=>loader(page.page-1)));
    target.append(node('span',` Page ${page.page+1} `)); if(page.hasNext) target.append(button('Next',()=>loader(page.page+1))); }
  function cards(id, items, render, empty) { const target=document.getElementById(id); target.replaceChildren();
    if(!items.length) target.append(node('p',empty)); else items.forEach(item=>target.append(render(item))); }
  async function directory(page=0) {
    const filter=new FormData(document.getElementById('directoryFilter')); const params=new URLSearchParams({department:filter.get('department')||'ALL',page});
    const result=await api((filter.has('mentors')?'/mentors':'')+'?'+params);
    cards('directoryResults',result.items,p=>{const card=node('article','', 'card');card.append(node('h3',p.displayName),node('p',`${p.department} · ${p.degree} · ${p.graduationYear}`));
      if(p.currentCompany||p.currentDesignation)card.append(node('p',[p.currentDesignation,p.currentCompany].filter(Boolean).join(' · ')));
      if(p.location)card.append(node('p',p.location));if(p.bio)card.append(node('p',p.bio));
      if(p.willingToMentor)card.append(node('p','Open to mentorship'));if(p.willingToRefer)card.append(node('p','Open to referrals'));
      if(p.linkedInUrl)card.append(link(p.linkedInUrl,'Open LinkedIn profile'));return card;},'No verified, opted-in profiles match this filter.');
    pagination('directoryPager',result,directory);
  }
  async function profile() {
    const p=await api('/profile');const form=document.getElementById('profileForm');
    const mapping={company:'currentCompany',designation:'currentDesignation',linkedin:'linkedInUrl',mentor:'willingToMentor',refer:'willingToRefer',expectedVersion:'version'};
    for(const input of form.elements){if(!input.name)continue;const value=p?.[mapping[input.name]||input.name];if(input.type==='checkbox')input.checked=value===true;else input.value=value??(input.name==='expectedVersion'?0:'');}
    document.getElementById('verificationStatus').textContent=p?.verifiedAlumni?'Verified membership. Degree/year corrections require university review.':'Membership not verified. Save your profile for independent university review.';
  }
  function jobCard(j, own=false){const card=node('article','','card');card.append(node('h3',j.jobTitle),node('p',`${j.companyName} · ${j.jobType}`),node('p',j.location),node('p',j.description),node('p',`Department: ${j.targetDepartment}`));
    if(j.applicationLinkOrEmail)card.append(link(j.applicationLinkOrEmail,'Open application contact'));
    if(own){card.append(node('p',j.active?'Active':'Closed'),button(j.active?'Close listing':'Reopen listing',async()=>{await api(`/jobs/${j.id}/state`,'PUT',{expectedVersion:j.version,active:!j.active});await Promise.all([jobs(),ownJobs()]);announce('Listing status saved.');}));}return card;}
  async function jobs(page=0){const department=new FormData(document.getElementById('jobFilter')).get('department')||'ALL';const result=await api('/jobs?'+new URLSearchParams({department,page}));cards('jobResults',result.items,j=>jobCard(j),'No active opportunities.');pagination('jobPager',result,jobs);}
  async function ownJobs(page=0){const result=await api('/jobs/mine?page='+page);cards('ownJobResults',result.items,j=>jobCard(j,true),'You have no listings.');pagination('ownJobPager',result,ownJobs);}
  function reviewCard(p){const card=node('article','','card');card.append(node('h3',p.displayName),node('p',`Profile ${p.id} · ${p.department} · ${p.degree} · ${p.graduationYear}`),node('p',p.verifiedAlumni?'Verified':'Awaiting verification'));
    card.append(button(p.verifiedAlumni?'Revoke verification':'Verify against university records',async()=>{if(!confirm('Confirm that you independently checked this identity and graduation record using authorized university records.'))return;await api(`/profiles/${p.id}/verification`,'PUT',{expectedVersion:p.version,verified:!p.verifiedAlumni});await review();document.getElementById('reviewLookupResult').replaceChildren();announce('Verification decision saved; the owner has an inbox notification.');}));return card;}
  async function review(page=0){const result=await api('/review?page='+page);cards('reviewResults',result.items,reviewCard,'No profiles awaiting review.');pagination('reviewPager',result,review);}
  const loaders={directory,profile,jobs:async()=>{await jobs();await ownJobs();},review};
  document.querySelectorAll('[data-section]').forEach(b=>b.addEventListener('click',()=>run(b,async()=>{document.querySelectorAll('main > section').forEach(s=>s.hidden=true);document.getElementById(b.dataset.section+'Section').hidden=false;await loaders[b.dataset.section]();})));
  function submit(id, action){const form=document.getElementById(id);if(form)form.addEventListener('submit',e=>{e.preventDefault();run(e.submitter||form.querySelector('button'),()=>action(form));});}
  submit('directoryFilter',()=>directory());submit('jobFilter',()=>jobs());
  submit('profileForm',async form=>{const data=Object.fromEntries(new FormData(form));form.querySelectorAll('[type=checkbox]').forEach(i=>data[i.name]=i.checked);data.expectedVersion=Number(data.expectedVersion);data.graduationYear=Number(data.graduationYear);await api('/profile','POST',data);await profile();announce('Profile saved. Your visibility preferences are effective.');});
  submit('jobForm',async form=>{await api('/jobs','POST',{...Object.fromEntries(new FormData(form)),requestKey});requestKey=crypto.randomUUID();form.reset();await jobs();await ownJobs();announce('Opportunity published.');});
  submit('reviewLookup',async form=>{const p=await api('/profiles/'+Number(new FormData(form).get('id')));document.getElementById('reviewLookupResult').replaceChildren(reviewCard(p));});
  directory().catch(e=>announce(e.message));
})();
