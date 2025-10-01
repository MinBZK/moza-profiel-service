using System.Text;
using System.Text.Json.Serialization;

using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;

using ProfielService.Data;
using ProfielService.Repositories;
using ProfielService.Serialization;
using ProfielService.services.clients;
using ProfielService.Services;

var builder = WebApplication.CreateBuilder(args);

// Authentication with JWT Bearer, note hier moet een echte secret key komen
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        var secretKey = builder.Configuration.GetValue<string>("SecretKey");
        var key = Encoding.ASCII.GetBytes(secretKey);

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = false,
            ValidateAudience = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(key)
        };
    });

builder.Services.AddAuthorization();


// Configure Swagger with Auth
builder.Services.AddEndpointsApiExplorer();

builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo { Title = "Profiel Service API", Version = "v1" });

    options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "Paste your JWT token below. 'Bearer' will be added automatically.",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT"
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference
                {
                    Type = ReferenceType.SecurityScheme,
                    Id = "Bearer"
                }
            },
            new string[] { }
        }
    });
});

builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

// JSON options
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.TypeInfoResolverChain.Insert(0, AppJsonSerializerContext.Default);
});

builder.Services.AddDbContext<ProfielDbContext>(options =>
{
    var env = builder.Environment; // IWebHostEnvironment
    if (env.IsDevelopment())
    {
        // Use SQLite locally
        var sqliteConnectionString = builder.Configuration.GetConnectionString("Sqlite");
        options.UseSqlite(sqliteConnectionString, innerOptions =>
        {
            innerOptions.MigrationsAssembly(typeof(Program).Assembly.GetName().Name);
        });
    }
    else
    {
        // Use PostgreSQL in other environments
        var postgresConnectionString = builder.Configuration.GetConnectionString("Default");
        options.UseNpgsql(postgresConnectionString, innerOptions =>
        {
            innerOptions.MigrationsAssembly(typeof(Program).Assembly.GetName().Name);
            innerOptions.UseQuerySplittingBehavior(QuerySplittingBehavior.SplitQuery);
        });
    }
});

builder.Services.AddAutoMapper(typeof(Program));

builder.Services.AddScoped<OndernemingService>();
builder.Services.AddScoped<OndernemingRepository>();
builder.Services.AddScoped<AuditLogService>();
builder.Services.AddScoped<AuditLogRepository>();
builder.Services.AddMemoryCache();
builder.Services.AddSingleton<KvkProfielClientService>();
builder.Services.AddSingleton<EmailVerificatieClient>();


builder.Services.AddHttpClient<KvkProfielClient>(client =>
{
    client.BaseAddress = new Uri("https://developers.kvk.nl/test/api/v1/");
});

builder.Services.AddHttpClient<EmailVerificatieClient>(client =>
{
    client.BaseAddress = new Uri("https://verificatie.notifynl.nl/");
});


builder.Services.AddControllers();
builder.Services.AddHttpContextAccessor();


var app = builder.Build();

using var scope = app.Services.CreateScope();

var db = scope.ServiceProvider.GetRequiredService<ProfielDbContext>();
db.Database.Migrate();

app.MapControllers();
app.UseSwagger();
app.UseSwaggerUI();
app.UseAuthentication();
app.UseAuthorization();

app.Run();
