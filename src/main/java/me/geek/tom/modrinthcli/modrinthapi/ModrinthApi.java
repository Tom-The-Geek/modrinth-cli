package me.geek.tom.modrinthcli.modrinthapi;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

import java.util.List;

import static me.geek.tom.modrinthcli.ModrinthCli.GSON;
import static me.geek.tom.modrinthcli.ModrinthCli.OKHTTP;

public interface ModrinthApi {
    @GET("/api/v1/mod/{id}/version") Call<List<ModVersion>> getModVersions(@Path("id") String id);
    @GET("/api/v1/mod/{id}") Call<ModDetails> getMod(@Path("id") String id);
    @GET("/api/v1/version/{id}") Call<ModVersion> getVersion(@Path("id") String id);

    static ModrinthApi get() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.modrinth.com/")
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .client(OKHTTP)
                .build();

        return retrofit.create(ModrinthApi.class);
    }
}
