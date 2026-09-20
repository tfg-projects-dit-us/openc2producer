const form = document.querySelector('#queries');
const investigation = document.querySelector('#investigation');
const sendButton = document.querySelector('#sendCommand');
const previewText = document.querySelector('#previewJson');
const previewMessage = document.querySelector('#previewMessage');
const previewTitle = document.querySelector('#previewTitle');
const message = document.querySelector('#message');
const result = document.querySelector('#result');
const command = document.querySelector('#command');
const httpStatus = document.querySelector('#httpStatus');
const responseBody = document.querySelector('#response');
const names = { capabilities: 'Consultar capacidades', huntflows: 'Consultar investigaciones',
    datasources: 'Consultar fuentes', investigate: 'Validar petición userlogon' };

let selected = null;
let prepared = null;
let revision = 0;
let previewRequest = null;
let timer = null;
let sending = false;

function snapshot(operation) {
    const fields = new URLSearchParams({ operation });
    if (operation === 'investigate') {
        fields.set('username', document.querySelector('#username').value);
        const level = document.querySelector('#vigilanceLevel').value;
        if (level) fields.set('vigilance_level', level);
    }
    return fields;
}

function clearPreview() {
    revision++;
    prepared = null;
    sendButton.disabled = true;
    previewText.textContent = '';
    previewRequest?.abort();
    clearTimeout(timer);
}

async function prepare(operation) {
    if (sending) return;
    selected = operation;
    clearPreview();
    const currentRevision = revision;
    const fields = snapshot(operation);
    previewRequest = new AbortController();
    previewTitle.textContent = names[operation];
    document.querySelectorAll('[data-operation]').forEach(button => {
        button.setAttribute('aria-pressed', String(button.dataset.operation === operation));
    });
    previewMessage.textContent = 'Generando el comando en Java…';
    try {
        // Solo se prepara el comando: este endpoint no contacta con el Consumer.
        const response = await fetch('/api/preview', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: fields.toString(),
            signal: previewRequest.signal
        });
        const json = await response.text();
        if (currentRevision !== revision) return;
        if (!response.ok) {
            previewMessage.textContent = json;
            return;
        }
        previewText.textContent = JSON.stringify(JSON.parse(json), null, 2);
        prepared = { operation, fields, json };
        sendButton.disabled = false;
        sendButton.textContent = operation === 'investigate' ? 'Enviar para validación' : 'Enviar al Consumer';
        previewMessage.textContent = 'Comando preparado. Todavía no se ha enviado al Consumer.';
    } catch (error) {
        if (currentRevision === revision && error.name !== 'AbortError') {
            previewMessage.textContent = 'No se pudo obtener la vista previa del Producer.';
        }
    }
}

form.addEventListener('submit', event => {
    event.preventDefault();
    prepare(event.submitter?.value || 'capabilities');
});
investigation.addEventListener('submit', event => {
    event.preventDefault();
    prepare('investigate');
});
investigation.addEventListener('input', () => {
    if (selected !== 'investigate' || sending) return;
    clearPreview();
    previewMessage.textContent = 'Los parámetros han cambiado. Actualizando la vista previa…';
    timer = setTimeout(() => prepare('investigate'), 250);
});

sendButton.addEventListener('click', async () => {
    if (!prepared || sending) return;
    const url = document.querySelector('#consumerUrl');
    if (!url.reportValidity()) return;
    // Conservamos la selección cuyos parámetros se han mostrado en la vista previa.
    const request = prepared;
    sending = true;
    clearTimeout(timer);
    const controls = document.querySelectorAll('button, input, select');
    controls.forEach(control => { control.disabled = true; });
    result.hidden = false;
    command.textContent = previewText.textContent;
    httpStatus.textContent = 'Sin respuesta HTTP';
    responseBody.textContent = '';
    message.textContent = 'Enviando al Consumer…';
    let contentType = 'text/plain; charset=UTF-8';
    let body = url.value.trim();
    if (request.operation === 'investigate') {
        const fields = new URLSearchParams(request.fields);
        fields.delete('operation');
        fields.set('consumerUrl', body);
        contentType = 'application/x-www-form-urlencoded; charset=UTF-8';
        body = fields.toString();
    }
    try {
        const response = await fetch('/api/' + request.operation, {
            method: 'POST', headers: { 'Content-Type': contentType }, body
        });
        const text = await response.text();
        const status = response.headers.get('X-Consumer-Status');
        const sent = response.headers.get('X-OpenC2-Command');
        if (sent) command.textContent = JSON.stringify(JSON.parse(sent), null, 2);
        if (status !== null) {
            httpStatus.textContent = status;
            // El cuerpo recibido siempre es texto: nunca se interpreta como HTML.
            responseBody.textContent = text;
            message.textContent = status === '501'
                ? 'Funcionalidad no implementada. No se ha ejecutado ninguna investigación.'
                : Number(status) >= 400 ? 'El Consumer ha devuelto un error.' : 'Respuesta recibida del Consumer.';
        } else {
            message.textContent = text || 'No se pudo completar la petición.';
        }
    } catch (error) {
        message.textContent = 'No se pudo conectar con el Producer o leer su respuesta.';
    } finally {
        sending = false;
        controls.forEach(control => { control.disabled = false; });
        sendButton.disabled = !prepared;
    }
});
