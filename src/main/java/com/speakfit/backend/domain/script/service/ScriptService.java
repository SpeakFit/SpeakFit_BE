package com.speakfit.backend.domain.script.service;

import com.speakfit.backend.domain.script.dto.req.AddScriptReq;
import com.speakfit.backend.domain.script.dto.res.AddScriptRes;
public interface ScriptService {
    /**
 * Adds a new script using the provided request data.
 *
 * @param req request DTO containing the script details to create
 * @return an AddScriptRes containing the result of the add operation and created script information
 */
    AddScriptRes addScript(AddScriptReq.Request req);
}