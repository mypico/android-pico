//------------------------------------------------------------------------------
// Copyright 2014 Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Description: See the class level JavaDoc comments.
//------------------------------------------------------------------------------

package com.microsoft.live;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONObject;

/**
 * PostRequest is a subclass of a BodyEnclosingApiRequest and performs a Post request.
 */
class PostRequest extends EntityEnclosingApiRequest<JSONObject> {

    public static final String METHOD = HttpPost.METHOD_NAME;

    /**
     * Constructs a new PostRequest and initializes its member variables.
     *
     * @param session with the access_token
     * @param client to make Http requests on
     * @param path of the request
     * @param entity body of the request
     */
    public PostRequest(LiveConnectSession session,
                       HttpClient client,
                       String path,
                       HttpEntity entity) {
        super(session, client, JsonResponseHandler.INSTANCE, path, entity);
    }

    /** @return the string "POST" */
    @Override
    public String getMethod() {
        return METHOD;
    }

    /**
     * Factory method override that constructs a HttpPost and adds a body to it.
     *
     * @return a HttpPost with the properly body added to it.
     */
    @Override
    protected HttpUriRequest createHttpRequest() throws LiveOperationException {
        final HttpPost request = new HttpPost(this.requestUri.toString());

        request.setEntity(this.entity);

        return request;
    }
}
