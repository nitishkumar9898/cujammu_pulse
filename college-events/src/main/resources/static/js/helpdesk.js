'use strict';
(() => {
  const base='/api/v1/cujammu/helpdesk';const csrf=document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader=document.querySelector('meta[name="_csrf_header"]').content;let view='mine',current=null;
  const keys={create:crypto.randomUUID(),reply:crypto.randomUUID(),triage:crypto.randomUUID(),escalate:crypto.randomUUID(),reopen:crypto.randomUUID()};
  const el=id=>document.getElementById(id);
  function say(text){el('notice').textContent=text;el('notice').hidden=false;}
  function node(tag,text){const n=document.createElement(tag);n.textContent=text??'';return n;}
  function button(text,action){const b=node('button',text);b.type='button';b.addEventListener('click',()=>run(b,action));return b;}
  async function run(control,action){control.disabled=true;try{await action();}catch(error){say(error.message);}finally{control.disabled=false;}}
  async function api(path,method='GET',body){const response=await fetch(base+path,{method,credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json','Content-Type':'application/json',[csrfHeader]:csrf},body:body===undefined?undefined:JSON.stringify(body)});
    if(response.redirected||response.status===401)throw new Error('Your session expired. Sign in again.');
    if(!response.ok)throw new Error(({400:'Check all fields, including required resolution notes.',403:'You do not have access to this ticket or action.',404:'This ticket or module is unavailable.',409:'This ticket changed or the action is not valid in its current state. Refresh and review before retrying.'})[response.status]||'The request did not complete. Retry when connected.');
    return response.json();}
  function show(id){['listSection','createSection','detailSection'].forEach(section=>el(section).hidden=section!==id);}
  function pager(id,result,load){const p=el(id);p.replaceChildren();if(result.page>0)p.append(button('Previous',()=>load(result.page-1)));p.append(node('span',`Page ${result.page+1}`));if(result.hasNext)p.append(button('Next',()=>load(result.page+1)));}
  async function list(page=0){show('listSection');el('listHeading').textContent=({mine:'My tickets',assigned:'Assigned tickets',all:'Support queue'})[view];
    const result=await api(({mine:'/my-tickets',assigned:'/assigned',all:'/all'})[view]+'?page='+page);el('ticketList').replaceChildren();
    if(!result.items.length)el('ticketList').append(node('p','No tickets in this view.'));
    result.items.forEach(t=>{const card=node('article');card.className='panel';card.append(node('h3',t.subject),node('p',t.ticketNumber),node('p',`${t.status} · ${t.priority}`));if(t.escalated)card.append(node('p','Escalation requested'));
      card.append(button(t.id?'Open private conversation':'Continue existing ticket',async()=>{if(!t.id)t=await api('/tickets/link-legacy','POST',{requestKey:crypto.randomUUID(),expectedVersion:t.version,ticketNumber:t.ticketNumber});await open(t.id);}));el('ticketList').append(card);});pager('listPager',result,list);}
  async function open(id){current=await api('/tickets/'+id);show('detailSection');render();await thread();}
  function render(){const t=current;el('ticketTitle').textContent=t.ticketNumber;el('ticketDetails').replaceChildren(node('h3',t.subject),node('p',t.description),node('p',`${t.status} · Priority ${t.priority} · Requested ${t.requestedPriority||'unspecified'}`),node('p',`Office: ${t.assignedOffice||'Awaiting assignment'}`));
    if(t.resolutionNotes)el('ticketDetails').append(node('p',`Resolution: ${t.resolutionNotes}`));if(t.escalated)el('ticketDetails').append(node('p','Escalation is awaiting support review.'));
    el('replyForm').hidden=!['OPEN','IN_PROGRESS'].includes(t.status);
    el('ownerActionForm').querySelector('[value="reopen"]').hidden=!t.canReopen;
    el('ownerActionForm').querySelector('[value="escalate"]').hidden=t.canReopen||t.escalated;
    el('ownerActionForm').hidden=t.escalated&&!t.canReopen;
    const form=el('triageForm');form.hidden=!t.canManage;
    if(t.canManage){form.elements.status.value=t.status;form.elements.priority.value=t.priority;form.elements.assignedOffice.value=t.assignedOffice||'STUDENT_AFFAIRS';form.elements.assignedStaffId.value=t.assignedStaffId||'';form.elements.resolutionNotes.value=t.resolutionNotes||'';
      form.elements.assignedOffice.disabled=document.body.dataset.admin!=='true';form.elements.assignedStaffId.disabled=document.body.dataset.admin!=='true';}}
  async function thread(page=0){const result=await api(`/tickets/${current.id}/thread?page=${page}`);el('thread').replaceChildren();if(!result.items.length)el('thread').append(node('p','No timeline entries yet.'));
    result.items.forEach(e=>{const row=node('article');row.className='message';row.append(node('strong',`${e.authorLabel} · ${e.kind}`),node('p',e.body),node('small',e.occurredAt+' UTC'));el('thread').append(row);});pager('threadPager',result,thread);}
  document.querySelectorAll('[data-view]').forEach(b=>b.addEventListener('click',()=>run(b,async()=>{view=b.dataset.view;await list();})));
  el('newTicket').addEventListener('click',()=>show('createSection'));el('refreshTicket').addEventListener('click',()=>run(el('refreshTicket'),()=>open(current.id)));
  function submit(id,action){el(id).addEventListener('submit',event=>{event.preventDefault();run(event.submitter,()=>action(el(id),event.submitter));});}
  submit('createForm',async form=>{const t=await api('/tickets','POST',{...Object.fromEntries(new FormData(form)),requestKey:keys.create});keys.create=crypto.randomUUID();form.reset();await open(t.id);say('Ticket saved. Response time depends on available support staff.');});
  submit('replyForm',async form=>{current=await api(`/tickets/${current.id}/replies`,'POST',{requestKey:keys.reply,expectedVersion:current.version,body:form.elements.body.value});keys.reply=crypto.randomUUID();form.reset();render();await thread();say('Private reply saved.');});
  submit('ownerActionForm',async(form,trigger)=>{const action=trigger.value;current=await api(`/tickets/${current.id}/${action}`,'POST',{requestKey:keys[action],expectedVersion:current.version,reason:form.elements.reason.value});keys[action]=crypto.randomUUID();form.reset();render();await thread();say('Ticket action saved.');});
  submit('triageForm',async form=>{current=await api(`/tickets/${current.id}/triage`,'PUT',{requestKey:keys.triage,expectedVersion:current.version,status:form.elements.status.value,priority:form.elements.priority.value,assignedOffice:form.elements.assignedOffice.value,assignedStaffId:form.elements.assignedStaffId.value?Number(form.elements.assignedStaffId.value):null,resolutionNotes:form.elements.resolutionNotes.value});keys.triage=crypto.randomUUID();render();await thread();say('Triage decision saved.');});
  list().catch(error=>say(error.message));
})();
