/**
 * Copyright 2013 Ricky Tobing
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance insert the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bingzer.android.dbv.sqlite;

import com.bingzer.android.dbv.IQuery;
import com.bingzer.android.dbv.IView;

/**
 * Created by Ricky Tobing on 8/19/13.
 */
class View extends Table implements IView {

    View (Database db, String name){
        super(db, name);
    }

    @Override
    public IQuery<Boolean> drop() {
        QueryImpl.DropImpl query = new QueryImpl.DropImpl();
        try{
            db.execSql("DROP VIEW " + getName());
            query.value = true;
        }
        catch (Exception e){
            query.value = false;
        }

        if(query.value) db.removeTable(this);
        return query;
    }

}
