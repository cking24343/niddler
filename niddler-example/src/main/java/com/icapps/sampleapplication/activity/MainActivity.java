package com.icapps.sampleapplication.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.icapps.sampleapplication.NiddlerSampleApplication;
import com.icapps.sampleapplication.R;
import com.icapps.sampleapplication.api.ExampleApi;
import com.icapps.sampleapplication.api.Post;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ExampleApi mApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApi = ((NiddlerSampleApplication) getApplication()).getJsonPlaceholderApi();

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mApi.getPosts().enqueue(new Callback<List<Post>>() {
                    @Override
                    public void onResponse(Call<List<Post>> call, Response<List<Post>> response) {

                    }

                    @Override
                    public void onFailure(Call<List<Post>> call, Throwable t) {

                    }
                });
            }
        });
    }
}