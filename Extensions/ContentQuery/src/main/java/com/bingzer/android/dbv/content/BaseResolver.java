/**
 * Copyright 2013 Ricky Tobing
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package com.bingzer.android.dbv.content;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.bingzer.android.dbv.IEntity;
import com.bingzer.android.dbv.IEntityList;
import com.bingzer.android.dbv.IQuery;
import com.bingzer.android.dbv.Util;
import com.bingzer.android.dbv.sqlite.ContentConfig;
import com.bingzer.android.dbv.sqlite.ContentDeleteImpl;
import com.bingzer.android.dbv.sqlite.ContentInsertImpl;
import com.bingzer.android.dbv.sqlite.ContentInsertWithImpl;
import com.bingzer.android.dbv.sqlite.ContentUpdateImpl;
import com.bingzer.android.dbv.sqlite.ContentUtils;
import com.bingzer.android.dbv.sqlite.UriUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Ricky Tobing on 8/23/13.
 */
abstract class BaseResolver implements IBaseResolver {

    final ContentConfig config;
    final Uri uri;
    final ContentResolver contentResolver;

    BaseResolver(ContentConfig config, Uri uri, Context context){
        this.contentResolver = context.getContentResolver();
        this.uri = uri;
        this.config = config;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public ContentConfig getConfig() {
        return config;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public IQuery.Delete delete(int id) {
        return delete(generateParamId(id));
    }

    @Override
    public IQuery.Delete delete(Collection<Integer> ids) {
        int[] idz = new int[ids.size()];
        int counter = 0;
        for (Integer id : ids) {
            idz[counter++] = id;
        }

        return delete(idz);
    }

    @Override
    public IQuery.Delete delete(int... ids) {
        if(ids != null && ids.length > 0){
            StringBuilder whereClause = new StringBuilder();

            whereClause.append(generateIdString()).append(" ");
            whereClause.append("IN (");
            for(int i = 0; i < ids.length; i++){
                whereClause.append(ids[i]);
                if(i < ids.length - 1){
                    whereClause.append(",");
                }
            }
            whereClause.append(")");

            return delete(whereClause.toString(), (Object)null);
        }
        else{
            // delete all
            return delete("1 = 1");
        }
    }

    @Override
    public IQuery.Delete delete(String condition) {
        return delete(condition, (Object)null);
    }

    @Override
    public IQuery.Delete delete(String whereClause, Object... whereArgs) {
        return new ContentDeleteImpl() {
            @Override
            public Integer query() {
                return value();
            }
        }.val(contentResolver.delete(uri, whereClause, Util.toStringArray(whereArgs)));
    }

    @Override
    public IQuery.Delete delete(IEntity entity) {
        return delete(entity.getId());
    }

    @Override
    public <E extends IEntity> IQuery.Delete delete(IEntityList<E> entityList) {
        int[] ids = new int[entityList.getEntityList().size()];
        for(int i = 0; i < ids.length; i++){
            ids[i] = entityList.getEntityList().get(i).getId();
        }
        return delete(ids);
    }

    @Override
    public IQuery.Insert insert(String[] columns, Object[] values) {
        final ContentValues contentValues = new ContentValues();
        for(int i = 0; i < columns.length; i++){
            ContentUtils.mapContentValuesFromGenericObject(contentValues, columns[i], values[i]);
        }

        return insert(contentValues);
    }

    @Override
    public IQuery.InsertWith insert(String... columns) {
        return new ContentInsertWithImpl(new ContentInsertWithImpl.ContentSet() {
            private ContentValues contentValues;
            private ContentInsertWithImpl query;
            private int value = -1;

            @Override
            public void onContentValuesSet(ContentInsertWithImpl query, ContentValues contentValues) {
                this.query = query;
                this.contentValues = contentValues;
                this.query.setUri(contentResolver.insert(uri, contentValues));
            }

            @Override
            public Integer query() {
                if(contentValues == null)
                    throw new IllegalArgumentException("ContentValues are not specified. Use IQuery.InsertWith.setUri()");
                // return
                return value;
            }
        }, columns);
    }

    @Override
    public IQuery.Insert insert(ContentValues contents) {
        return new ContentInsertImpl().setUri(contentResolver.insert(uri, contents));
    }

    @SuppressWarnings("unchecked")
    @Override
    public IQuery.Insert insert(IEntity entity) {
        // build content values..
        final EntityMapper mapper = new EntityMapper(this);
        final ContentValues contentValues = new ContentValues();
        entity.map(mapper);

        Iterator<String> keys = mapper.keySet().iterator();
        String idString = generateIdString();
        IEntity.Action<Integer> idSetter = null;
        while(keys.hasNext()){
            String key = keys.next();
            IEntity.Action action = mapper.get(key);

            // ignore if column = "Id"
            if(key.equalsIgnoreCase(idString)) {
                idSetter = action;
            }
            else if(action != null){
                ContentUtils.mapContentValuesFromAction(contentValues, key, action);
            }
        }

        IQuery.Insert insert = insert(contentValues);
        // assign the newly inserted id
        if(idSetter != null){
            idSetter.set(insert.query());
        }

        return insert;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends IEntity> IQuery.Insert insert(IEntityList<E> entityList) {
        if(config.getDefaultAuthority() == null)
            throw new IllegalArgumentException("Authority has not been set. Use ContentConfig.setDefaultAuthority() to set");

        final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        final IEntity.Action[] idSetters = new IEntity.Action[entityList.getEntityList().size()];
        final String[] uriStrings = new String[entityList.getEntityList().size()];

        for(int i = 0; i < entityList.getEntityList().size(); i++){
            final IEntity entity = entityList.getEntityList().get(i);
            // build content values..
            final EntityMapper mapper = new EntityMapper(this);
            final ContentValues contentValues = new ContentValues();
            entity.map(mapper);

            Iterator<String> keys = mapper.keySet().iterator();
            String idString = generateIdString();
            while(keys.hasNext()){
                String key = keys.next();
                IEntity.Action action = mapper.get(key);

                // ignore if column = "Id"
                if(key.equalsIgnoreCase(idString)) {
                    idSetters[i] = action;
                }
                else if(action != null){
                    ContentUtils.mapContentValuesFromAction(contentValues, key, action);
                }
            }

            ContentProviderOperation operation =
                    ContentProviderOperation.newInsert(uri)
                            .withValues(contentValues)
                            .build();
            operationList.add(operation);
        }

        try{
            ContentProviderResult[] results =
                    contentResolver.applyBatch(getConfig().getDefaultAuthority(), operationList);
            for(int i = 0; i < results.length; i++){
                idSetters[i].set(UriUtil.parseIdFromUri(results[i].uri));
                uriStrings[i] = results[i].uri.toString();
            }
        }
        catch (Exception e){
            throw new Error(e);
        }

        return new ContentInsertImpl(){

            @Override
            public Integer query(){
                return uriStrings.length;
            }

            @Override
            public String toString(){
                return Util.join(";", uriStrings);
            }
        };
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int selectId(String condition) {
        return selectId(condition, (Object)null);
    }

    @Override
    public abstract int selectId(String whereClause, Object... args);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public IQuery.Update update(String column, Object value, int id) {
        return update(column, value, generateParamId(id));
    }

    @Override
    public IQuery.Update update(String column, Object value, String condition) {
        return update(column, value, condition, (Object)null);
    }

    @Override
    public IQuery.Update update(String column, Object value, String whereClause, Object... whereArgs) {
        return update(new String[]{column}, new Object[]{ value }, whereClause, whereArgs);
    }

    @Override
    public IQuery.Update update(String[] columns, Object[] values, String condition) {
        return update(columns, values, condition, (Object)null);
    }

    @Override
    public IQuery.Update update(String[] columns, Object[] values, String whereClause, Object... whereArgs) {
        final ContentValues contentValues = new ContentValues();
        for(int i = 0; i < columns.length; i++){
            ContentUtils.mapContentValuesFromGenericObject(contentValues, columns[i], values[i]);
        }
        return update(contentValues, whereClause, whereArgs);
    }

    @Override
    public IQuery.Update update(ContentValues contents, int id) {
        return update(contents, generateParamId(id), (Object)null);
    }

    @Override
    public IQuery.Update update(ContentValues contents, String whereClause, Object... whereArgs) {
        ContentUpdateImpl query = new ContentUpdateImpl();
        String[] args = Util.toStringArray(whereArgs);
        query.val(contentResolver.update(uri, contents, whereClause, args));
        return query;
    }

    @Override
    public IQuery.Update update(IEntity entity) {
        if(entity.getId() < 0) throw new IllegalArgumentException("Id has to be over than 0");

        final EntityMapper mapper = new EntityMapper(this);
        final ContentValues contentValues = new ContentValues();
        entity.map(mapper);

        for (String key : mapper.keySet()) {
            // ignore if "Id"
            if (key.equalsIgnoreCase(generateIdString())) continue;

            IEntity.Action action = mapper.get(key);
            if (action != null) {
                ContentUtils.mapContentValuesFromAction(contentValues, key, action);
            }
        }

        return update(contentValues, entity.getId());
    }

    @Override
    public <E extends IEntity> IQuery.Update update(IEntityList<E> entityList) {
        if(getConfig().getDefaultAuthority() == null)
            throw new IllegalArgumentException("Authority has not been set. Use IResolver.setDefaultAuthority() to set");

        final ContentUpdateImpl query = new ContentUpdateImpl();
        final ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

        for(int i = 0; i < entityList.getEntityList().size(); i++){
            final IEntity entity = entityList.getEntityList().get(i);
            // build content values..
            final EntityMapper mapper = new EntityMapper(this);
            final ContentValues contentValues = new ContentValues();
            entity.map(mapper);

            Iterator<String> keys = mapper.keySet().iterator();
            String idString = generateIdString();
            while(keys.hasNext()){
                String key = keys.next();
                IEntity.Action action = mapper.get(key);

                // ignore if column = "Id"
                if(!key.equalsIgnoreCase(idString)) {
                    ContentUtils.mapContentValuesFromAction(contentValues, key, action);
                }
            }

            ContentProviderOperation operation =
                    ContentProviderOperation.newUpdate(uri)
                            .withValues(contentValues)
                            .withSelection(generateParamId(entity.getId()), null)
                            .build();
            operationList.add(operation);
        }

        try{
            int count = 0;
            ContentProviderResult[] results =
                    contentResolver.applyBatch(getConfig().getDefaultAuthority(), operationList);
            for(ContentProviderResult result : results){
                count += result.count;
            }
            query.val(count);
        }
        catch (Exception e){
            throw new Error(e);
        }

        return query;
    }

    @Override
    public boolean has(int id) {
        return has(generateParamId(id));
    }

    @Override
    public boolean has(String condition) {
        return has(condition, (Object) null);
    }

    @Override
    public abstract boolean has(String whereClause, Object... whereArgs);

    @Override
    public int count() {
        return count(null);
    }

    @Override
    public int count(String condition) {
        return count(condition, (Object)null);
    }

    @Override
    public abstract int count(String whereClause, Object... whereArgs);

    @Override
    public String generateIdString(){
        if(config.getAppendTableNameForId()){
            String tableName = "";
            Matcher m = getUriPattern().matcher(uri.toString());
            if(m.find()){
                tableName = m.group(3);
            }
            return tableName + config.getIdNamingConvention();
        }
        return config.getIdNamingConvention();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    String generateParamId(int id){
        return generateIdString() + " = " + id;
    }

    Pattern getUriPattern(){
        return Pattern.compile("(content://)(.+)/(\\w+)/?((\\d+)|(#)|(\\*))?");
    }
}
